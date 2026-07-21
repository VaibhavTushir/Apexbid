package org.vaibhav.apexbid.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vaibhav.apexbid.config.RedisKeysConfig;
import org.vaibhav.apexbid.entity.Auction;
import org.vaibhav.apexbid.entity.Transaction;
import org.vaibhav.apexbid.entity.Wallet;
import org.vaibhav.apexbid.enums.AuctionStatus;
import org.vaibhav.apexbid.enums.TransactionType;
import org.vaibhav.apexbid.repository.AuctionRepository;
import org.vaibhav.apexbid.repository.TransactionRepository;
import org.vaibhav.apexbid.repository.WalletRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionSettlementService {

    private final AuctionRepository auctionRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> finalizeSettlementScript;
    private final RedisKeysConfig redisKeys; // Injected central config

    /**
     * Phase 1: The Postgres Transaction.
     * Returns true if successful (or if already completed previously).
     */
    @Transactional
    @Retryable(
            includes = {OptimisticLockingFailureException.class},
            maxRetries = 2,
            delay = 200,
            multiplier = 2.0
    )
    public boolean commitToDatabase(Long auctionId, Map<Object, Object> redisData) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new RuntimeException("Auction not found: " + auctionId));

        // Idempotency Check
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            log.info("Auction {} DB already settled. Skipping to Redis cleanup.", auctionId);
            return true;
        }
        if (redisData == null || redisData.isEmpty()) {
            throw new IllegalStateException("CRITICAL: Redis state for auction " + auctionId + " is completely missing. Cannot safely determine winner. Forcing to DLQ.");
        }
        String winnerStr = (String) redisData.get("winner_id");
        String bidStr = (String) redisData.get("winning_bid");
        String endTimeStr = (String) redisData.get("end_time");

        // If no bids were placed
        if (winnerStr == null || winnerStr.isBlank()) {
            auction.setStatus(AuctionStatus.UNSOLD);
            auctionRepository.save(auction);
            return true;
        }

        Long winnerId = Long.valueOf(winnerStr);
        long winningBid = Long.parseLong(bidStr);
        long endTimeEpoch = endTimeStr != null ? Long.parseLong(endTimeStr) : auction.getEndTime().toEpochMilli();
        long escrowAmount = calculateEscrow(winningBid);

        Wallet winnerWallet = walletRepository.findById(winnerId)
                .orElseThrow(() -> new RuntimeException("Winner wallet missing"));
        Wallet sellerWallet = walletRepository.findById(auction.getSeller().getId())
                .orElseThrow(() -> new RuntimeException("Seller wallet missing"));

        winnerWallet.setBalance(winnerWallet.getBalance() - escrowAmount);
        sellerWallet.setBalance(sellerWallet.getBalance() + escrowAmount);
        walletRepository.saveAll(List.of(winnerWallet, sellerWallet));

        Transaction escrowTx = Transaction.builder()
                .sender(winnerWallet.getUser())
                .receiver(sellerWallet.getUser())
                .auction(auction)
                .amount(escrowAmount)
                .transactionType(TransactionType.ESCROW_TRANSFER)
                .build();
        transactionRepository.save(escrowTx);

        auction.setStatus(AuctionStatus.ESCROW_SECURED);
        auction.setWinner(winnerWallet.getUser());
        auction.setWinningBid(winningBid);
        auction.setEndTime(Instant.ofEpochMilli(endTimeEpoch));
        auctionRepository.save(auction);

        return true;
    }

    /**
     * Phase 2: The Redis Cleanup.
     */
    public void executeRedisCleanup(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElseThrow();

        String winnerId = "";
        long escrowAmount = 0;

        if (auction.getWinner() != null) {
            winnerId = String.valueOf(auction.getWinner().getId());
            escrowAmount = calculateEscrow(auction.getWinningBid());
        }

        // Applied centralized prefixes here
        String auctionKey = redisKeys.getAuctionHashPrefix() + auction.getId();
        String winnerWalletKey = redisKeys.getWalletHashPrefix() + winnerId;
        String sellerWalletKey = redisKeys.getWalletHashPrefix() + auction.getSeller().getId();

        stringRedisTemplate.execute(
                finalizeSettlementScript,
                List.of(auctionKey, winnerWalletKey, sellerWalletKey),
                String.valueOf(auction.getId()),
                winnerId,
                String.valueOf(escrowAmount),
                String.valueOf(auction.getSeller().getId()),
                redisKeys.getChannelWalletUpdates(),
                redisKeys.getWinningSetPrefix()
        );
    }

    private long calculateEscrow(long bidAmount) {
        if (bidAmount < 5000) return 100;
        else if (bidAmount < 10000) return 1000;
        else if (bidAmount < 50000) return 2500;
        else if (bidAmount < 100000) return 5000;
        else if (bidAmount < 500000) return 10000;
        else if (bidAmount < 1000000) return 25000;
        else return 50000;
    }
}