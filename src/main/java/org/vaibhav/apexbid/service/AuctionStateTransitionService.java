package org.vaibhav.apexbid.service;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
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

                // 1. Pipeline the Lua script executions
                List<Object> scriptResults = stringRedisTemplate.executePipelined(new SessionCallback<>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <K, V> Object execute(@NonNull RedisOperations<K, V> operations) {
                        // Cast the operations to String-based so our script signature matches
                        RedisOperations<String, String> stringOps = (RedisOperations<String, String>) operations;

                        for (String auctionId : toActivate) {
                            List<String> keys = List.of(
                                    "auction:" + auctionId,
                                    "auctions:upcoming",
                                    "auctions:active",
                                    "auctions:highest_bids",
                                    "auctions:most_active"
                            );
                            stringOps.execute(activateAuctionScript, keys, auctionId);
                        }
                        return null; // Spring Data Redis requires pipelines to return null here
                    }
                });

                // 2. Process the pipeline results
                // (scriptResults contains the 1s and 0s returned by Lua, perfectly ordered)
                List<Long> auctionIdsToActivate = new ArrayList<>();
                int index = 0;

                for (String auctionId : toActivate) {
                    Long result = (Long) scriptResults.get(index++);
                    if (Long.valueOf(1).equals(result)) {
                        auctionIdsToActivate.add(Long.parseLong(auctionId));
                        log.info("[LIFECYCLE] Auction {} went LIVE atomically via Pipeline.", auctionId);
                    }
                }

                // 3. Sync successful transitions to PostgreSQL
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

                // 1. Open the Pipeline Buffer
                List<Object> scriptResults = stringRedisTemplate.executePipelined(new SessionCallback<>() {

                    @Override
                    @SuppressWarnings("unchecked")
                    public <K, V> Object execute(@NonNull RedisOperations<K, V> operations) {
                        // Cast the generic operations back to String for the Lua script
                        RedisOperations<String, String> stringOps = (RedisOperations<String, String>) operations;

                        for (String auctionId : toSettle) {
                            List<String> keys = List.of(
                                    "auction:" + auctionId,
                                    "auctions:active",
                                    "auctions:highest_bids",
                                    "auctions:most_active",
                                    "queue:settlement"
                            );

                            // Queue the script
                            stringOps.execute(queueSettlementScript, keys, auctionId);
                        }

                        // Spring Data Redis requirement
                        return null;
                    }
                });

                // 2. Process the perfectly ordered results
                int index = 0;
                for (String auctionId : toSettle) {
                    Long result = (Long) scriptResults.get(index++);
                    if (Long.valueOf(1).equals(result)) {
                        log.info("[LIFECYCLE] Auction {} expired. Committed to settlement queue.", auctionId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[LIFECYCLE ERROR] Settlement queuing sweep failed for leader {}", nodeId, e);
        }
    }
}

