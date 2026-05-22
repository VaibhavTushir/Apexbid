package org.vaibhav.apexbid.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.vaibhav.apexbid.repository.AuctionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class AuctionStateTransitionService {
    private final StringRedisTemplate stringRedisTemplate;
    private final AuctionRepository auctionRepository;
    private final DefaultRedisScript<Long> activateAuctionScript;
    private final DefaultRedisScript<Long> queueSettlementScript;

    public AuctionStateTransitionService(StringRedisTemplate stringRedisTemplate,
                                         AuctionRepository auctionRepository,
                                         DefaultRedisScript<Long> activateAuctionScript,
                                         DefaultRedisScript<Long> queueSettlementScript) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.auctionRepository = auctionRepository;
        this.activateAuctionScript = activateAuctionScript;
        this.queueSettlementScript = queueSettlementScript;
    }

    public void activateUpcomingAuctions(String nodeId, String lockKey) {
        if (!nodeId.equals(stringRedisTemplate.opsForValue().get(lockKey))) {
            return;
        }
        try {
            long nowEpoch = Instant.now().toEpochMilli();
            Set<String> toActivate = stringRedisTemplate.opsForZSet().rangeByScore("auctions:upcoming", 0, nowEpoch);
            if (toActivate != null && !toActivate.isEmpty()) {
                List<Long> auctionIdsToActivate = new ArrayList<>();
                for (String auctionId : toActivate) {
                    List<String> keys = List.of(
                            "auction:" + auctionId,
                            "auctions:upcoming",
                            "auctions:active",
                            "auctions:highest_bids",
                            "auctions:most_active"
                    );
                    Long result = stringRedisTemplate.execute(
                            activateAuctionScript,
                            keys,
                            auctionId
                    );

                    if (Long.valueOf(1).equals(result)) {
                        auctionIdsToActivate.add(Long.parseLong(auctionId));
                        log.info("[LIFECYCLE] Auction {} went LIVE atomically in Redis.", auctionId);
                    }
                }
                if (!auctionIdsToActivate.isEmpty()) {
                    auctionRepository.bulkActivateAuctions(auctionIdsToActivate);
                    log.info("[LIFECYCLE] Synced {} auctions to ACTIVE in DB.", auctionIdsToActivate.size());
                }
            }
        } catch (Exception e) {
            log.error("[LIFECYCLE ERROR] Activation sweep failed for leader {}", nodeId, e);
        }
    }

    public void queueExpiredAuctions(String nodeId, String lockKey) {
        if (!nodeId.equals(stringRedisTemplate.opsForValue().get(lockKey))) {
            return;
        }
        try {
            long nowEpoch = Instant.now().toEpochMilli();
            Set<String> toSettle = stringRedisTemplate.opsForZSet().rangeByScore("auctions:active", 0, nowEpoch);
            if (toSettle != null && !toSettle.isEmpty()) {
                for (String auctionId : toSettle) {
                    List<String> keys = List.of(
                            "auction:" + auctionId,
                            "auctions:active",
                            "auctions:highest_bids",
                            "auctions:most_active",
                            "queue:settlement"
                    );
                    Long result = stringRedisTemplate.execute(
                            queueSettlementScript,
                            keys,
                            auctionId
                    );

                    if (Long.valueOf(1).equals(result)) {
                        log.info("[LIFECYCLE] Auction {} expired. Safely committed to settlement queue.", auctionId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[LIFECYCLE ERROR] Settlement queuing sweep failed for leader {}", nodeId, e);
        }
    }
}

