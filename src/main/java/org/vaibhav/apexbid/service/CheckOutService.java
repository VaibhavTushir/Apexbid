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
import org.vaibhav.apexbid.entity.Product;
import org.vaibhav.apexbid.entity.Transaction;
import org.vaibhav.apexbid.entity.Wallet;
import org.vaibhav.apexbid.enums.AuctionStatus;
import org.vaibhav.apexbid.enums.TransactionType;
import org.vaibhav.apexbid.repository.AuctionRepository;
import org.vaibhav.apexbid.repository.ProductRepository;
import org.vaibhav.apexbid.repository.TransactionRepository;
import org.vaibhav.apexbid.repository.WalletRepository;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckOutService {

    private final AuctionRepository auctionRepository;
    private final WalletRepository walletRepository;
    private final ProductRepository productRepository;
    private final TransactionRepository transactionRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> finalizeCheckOutScript;
    private final RedisKeysConfig redisKeys;
    private final WalletService walletService;

    public long getPendingBalance(Long auctionId, Long requestUserId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new RuntimeException("Auction not found"));

        if (!auction.getWinner().getId().equals(requestUserId) && !auction.getSeller().getId().equals(requestUserId)) {
            throw new SecurityException("Only the buyer or seller can view this balance.");
        }

        if (auction.getStatus() == AuctionStatus.SETTLED) return 0L;

        Transaction escrowTx = transactionRepository.findByAuction_IdAndTransactionType(auctionId, TransactionType.ESCROW_TRANSFER)
                .orElseThrow(() -> new RuntimeException("Escrow missing"));

        return auction.getWinningBid() - escrowTx.getAmount();
    }

    public void initiateCheckout(Long auctionId, Long requestUserId) {
        Auction auction = auctionRepository.findById(auctionId).orElseThrow();

        if (auction.getStatus() != AuctionStatus.ESCROW_SECURED) {
            throw new IllegalStateException("Auction is not ready for final payment.");
        }
        if (!auction.getWinner().getId().equals(requestUserId)) {
            throw new SecurityException("Only the winner can checkout.");
        }

        long remainingAmount = getPendingBalance(auctionId, requestUserId);
        Long sellerId = auction.getSeller().getId();

        String winnerWalletKey = redisKeys.getWalletHashPrefix() + requestUserId;
        String sellerWalletKey = redisKeys.getWalletHashPrefix() + sellerId;

        int attempts = 0;
        Long result = null;

        // Smart Retry Loop: Max 2 attempts in case BOTH wallets are missing from Redis
        while (attempts < 2) {
            result = stringRedisTemplate.execute(
                    finalizeCheckOutScript,
                    List.of(winnerWalletKey, sellerWalletKey, redisKeys.getAuctionsCheckoutQueue()),
                    String.valueOf(remainingAmount),
                    String.valueOf(requestUserId),
                    String.valueOf(sellerId),
                    String.valueOf(auctionId),
                    redisKeys.getChannelWalletUpdates()
            );

            // Break immediately on success (1L) or Insufficient Funds (-3L)
            if (result == 1L || result == -3L) {
                break;
            }

            // Handle Redis Cache Misses using your WalletService
            if (result == -1L) {
                log.warn("Buyer wallet missing in Redis. Attempting hydration.");
                if (!walletService.hydrateWalletToRedis(requestUserId)) {
                    throw new RuntimeException("Buyer wallet does not exist in the system.");
                }
            } else if (result == -2L) {
                log.warn("Seller wallet missing in Redis. Attempting hydration.");
                if (!walletService.hydrateWalletToRedis(sellerId)) {
                    throw new RuntimeException("Seller wallet does not exist in the system.");
                }
            }
            attempts++;
        }

        // Final Validation Post-Loop
        if (result == -1L || result == -2L) {
            throw new RuntimeException("CRITICAL: Wallet cache hydration failed after retries.");
        }
        if (result == -3L) {
            throw new IllegalStateException("Insufficient funds for final payment.");
        }

        log.info("Redis checkout successful for auction {}. Added to processing queue.", auctionId);
    }

    @Transactional
    @Retryable(includes = {OptimisticLockingFailureException.class}, maxRetries = 2, delay = 200, multiplier = 2.0)
    public boolean commitCheckoutToDatabase(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElseThrow();

        if (auction.getStatus() == AuctionStatus.SETTLED) return true;

        // Bypassing getPendingBalance here since Worker doesn't have a requestUserId
        Transaction escrowTx = transactionRepository.findByAuction_IdAndTransactionType(auctionId, TransactionType.ESCROW_TRANSFER).orElseThrow();
        long remainingAmount = auction.getWinningBid() - escrowTx.getAmount();

        Wallet winnerWallet = walletRepository.findById(auction.getWinner().getId()).orElseThrow();
        Wallet sellerWallet = walletRepository.findById(auction.getSeller().getId()).orElseThrow();

        winnerWallet.setBalance(winnerWallet.getBalance() - remainingAmount);
        sellerWallet.setBalance(sellerWallet.getBalance() + remainingAmount);
        walletRepository.saveAll(List.of(winnerWallet, sellerWallet));

        Transaction finalTx = Transaction.builder()
                .sender(winnerWallet.getUser())
                .receiver(sellerWallet.getUser())
                .auction(auction)
                .amount(remainingAmount)
                .transactionType(TransactionType.FINAL_PAYMENT)
                .build();
        transactionRepository.save(finalTx);

        Product product = auction.getProduct();
        product.setBuyer(auction.getWinner());
        productRepository.save(product);

        auction.setStatus(AuctionStatus.SETTLED);
        auctionRepository.save(auction);
        return true;
    }
}