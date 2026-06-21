package org.vaibhav.apexbid.worker;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.vaibhav.apexbid.config.RedisKeysConfig;
import org.vaibhav.apexbid.service.AuctionSettlementService;
import org.vaibhav.apexbid.service.DlqService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SettlementWorker {

    private final AuctionSettlementService settlementService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<String> fetchFromQueueScript; // Renamed variable
    private final RedisKeysConfig redisKeys;
    private final DlqService dlqService;
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = true;

    public SettlementWorker(AuctionSettlementService settlementService,
                            StringRedisTemplate stringRedisTemplate,
                            RedisScript<String> fetchFromQueueScript, // Renamed in constructor
                            RedisKeysConfig redisKeys,
                            DlqService dlqService) {
        this.settlementService = settlementService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.fetchFromQueueScript = fetchFromQueueScript;
        this.redisKeys = redisKeys;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.dlqService = dlqService;
    }

    @PostConstruct
    public void startWorker() {
        scheduler.execute(this::pollSettlementQueue);
        log.info("Auction Settlement Worker started (Dynamic Scheduled Mode)");
    }

    @PreDestroy
    public void stopWorker() {
        this.running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            log.info("Settlement Worker safely shut down.");
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void pollSettlementQueue() {
        if (!running) return;

        String auctionIdStr = null;
        try {
            long currentTime = System.currentTimeMillis();
            long visibilityTimeoutScore = currentTime + 60000;

            // Phase 1: Reliable Fetch via Lua (Using centralized keys and updated args)
            auctionIdStr = stringRedisTemplate.execute(
                    fetchFromQueueScript,
                    List.of(redisKeys.getAuctionsSettlementQueue(), redisKeys.getAuctionsSettlementProcessingZset()),
                    String.valueOf(currentTime),          // ARGV[1]
                    String.valueOf(visibilityTimeoutScore) // ARGV[2]
            );

            // Phase 2: Short polling backoff
            if (auctionIdStr == null) {
                scheduler.schedule(this::pollSettlementQueue, 1, TimeUnit.SECONDS);
                return;
            }

            Long auctionId = Long.valueOf(auctionIdStr);
            log.info("Processing settlement for auctionId: {}", auctionId);

            // Phase 3: Fetch volatile Redis State once (Using centralized prefix)
            Map<Object, Object> redisData = stringRedisTemplate.opsForHash().entries(redisKeys.getAuctionHashPrefix() + auctionIdStr);

            // Phase 4: Execute Database Transaction
            boolean dbSuccess = settlementService.commitToDatabase(auctionId, redisData);

            // Phase 5: Execute Redis Cleanup ONLY if DB succeeded
            if (dbSuccess) {
                settlementService.executeRedisCleanup(auctionId);

                // Clean up Processing ZSET (Using centralized key)
                stringRedisTemplate.opsForZSet().remove(redisKeys.getAuctionsSettlementProcessingZset(), auctionIdStr);
                log.info("Settlement completed successfully for auctionId: {}", auctionId);
            }

            // Instantly trigger the next loop to drain queue fast
            scheduler.execute(this::pollSettlementQueue);

        } catch (Exception e) {
            // Phase 6: Safety Net (DLQ)
            log.error("Fatal error for auction {}. Moving to PostgreSQL DLQ. Reason: {}", auctionIdStr, e.getMessage());

            if (auctionIdStr != null) {
                Long failedId = Long.valueOf(auctionIdStr);
                dlqService.logFailure(failedId, "SETTLEMENT", e);
                // Clean up Processing ZSET on failure (Using centralized key)
                stringRedisTemplate.opsForZSet().remove(redisKeys.getAuctionsSettlementProcessingZset(), auctionIdStr);
            }

            // Error Backoff to prevent infinite crash loops
            scheduler.schedule(this::pollSettlementQueue, 2, TimeUnit.SECONDS);
        }
    }
}