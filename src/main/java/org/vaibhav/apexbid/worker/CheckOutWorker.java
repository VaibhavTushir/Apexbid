package org.vaibhav.apexbid.worker;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.vaibhav.apexbid.config.RedisKeysConfig;
import org.vaibhav.apexbid.service.CheckOutService;
import org.vaibhav.apexbid.service.DlqService;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class CheckOutWorker {

    private final CheckOutService checkoutService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<String> fetchFromQueueScript;
    private final RedisKeysConfig redisKeys;
    private final ScheduledExecutorService scheduler;
    private final DlqService dlqService;
    private volatile boolean running = true;

    public CheckOutWorker(CheckOutService checkoutService,
                          StringRedisTemplate stringRedisTemplate,
                          RedisScript<String> fetchFromQueueScript,
                          RedisKeysConfig redisKeys, DlqService dlqService) {
        this.checkoutService = checkoutService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.fetchFromQueueScript = fetchFromQueueScript;
        this.redisKeys = redisKeys;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.dlqService = dlqService;
    }

    @PostConstruct
    public void startWorker() {
        scheduler.execute(this::pollCheckoutQueue);
        log.info("Auction Checkout Worker started");
    }

    @PreDestroy
    public void stopWorker() {
        this.running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                log.warn("Checkout Worker forced to shut down after timeout.");
            } else {
                log.info("Checkout Worker safely shut down.");
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
            log.warn("Checkout Worker shutdown was interrupted.");
        }
    }

    private void pollCheckoutQueue() {
        if (!running) return;

        String auctionIdStr = null;
        try {
            long currentTime = System.currentTimeMillis();
            long visibilityTimeoutScore = currentTime + 60000;

            auctionIdStr = stringRedisTemplate.execute(
                    fetchFromQueueScript,
                    List.of(redisKeys.getAuctionsCheckoutQueue(), redisKeys.getAuctionsCheckoutProcessingZset()),
                    String.valueOf(currentTime),          // ARGV[1]
                    String.valueOf(visibilityTimeoutScore) // ARGV[2]
            );

            if (auctionIdStr == null) {
                scheduler.schedule(this::pollCheckoutQueue, 1, TimeUnit.SECONDS);
                return;
            }

            Long auctionId = Long.valueOf(auctionIdStr);
            boolean dbSuccess = checkoutService.commitCheckoutToDatabase(auctionId);

            if (dbSuccess) {
                stringRedisTemplate.opsForZSet().remove(redisKeys.getAuctionsCheckoutProcessingZset(), auctionIdStr);
                log.info("Database checkout settled for auctionId: {}", auctionId);
            }

            scheduler.execute(this::pollCheckoutQueue);

        } catch (Exception e) {
            log.error("Fatal error committing checkout for auction {}.", auctionIdStr, e);
            if (auctionIdStr != null) {
                dlqService.logFailure(Long.valueOf(auctionIdStr), "CHECKOUT", e);
                stringRedisTemplate.opsForZSet().remove(redisKeys.getAuctionsCheckoutProcessingZset(), auctionIdStr);
            }
            scheduler.schedule(this::pollCheckoutQueue, 2, TimeUnit.SECONDS);
        }
    }
}