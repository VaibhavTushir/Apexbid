package org.vaibhav.apexbid.service;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.vaibhav.apexbid.dto.AuctionRedis;
import org.vaibhav.apexbid.enums.AuctionStatus;
import org.vaibhav.apexbid.repository.AuctionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PeriodicHydrationService {
    private final StringRedisTemplate stringRedisTemplate;
    private final AuctionRepository auctionRepository;
    private final AuctionQueryService auctionQueryService;
    private final DefaultRedisScript<Long> hydrateAuctionScript;

    public PeriodicHydrationService(StringRedisTemplate stringRedisTemplate,
                                    AuctionRepository auctionRepository,
                                    AuctionQueryService auctionQueryService,
                                    DefaultRedisScript<Long> hydrateAuctionScript) {

        this.stringRedisTemplate = stringRedisTemplate;
        this.auctionRepository = auctionRepository;
        this.auctionQueryService = auctionQueryService;
        this.hydrateAuctionScript = hydrateAuctionScript;
    }

    public void runRollingHydration(String nodeId, String lockKey) {
        if (!nodeId.equals(stringRedisTemplate.opsForValue().get(lockKey))) {
            return;
        }
        try {
            // 1-Hour lookahead window
            List<AuctionRedis> upcomingAuctions = auctionRepository.findAuctionsByStatusAndStartTimeLessThanEqual(
                    List.of(AuctionStatus.UPCOMING),
                    Instant.now().plusSeconds(3600)
            );

            if (upcomingAuctions != null && !upcomingAuctions.isEmpty()) {

                // 1. Open the Pipeline Buffer
                List<Object> scriptResults = stringRedisTemplate.executePipelined(new SessionCallback<>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <K, V> Object execute(@NonNull RedisOperations<K, V> operations) {
                        RedisOperations<String, String> stringOps = (RedisOperations<String, String>) operations;

                        for (AuctionRedis auction : upcomingAuctions) {
                            String auctionIdStr = auction.id().toString();
                            String auctionHashKey = "auction:" + auctionIdStr;
                            long startTimeEpoch = auction.startTime().toEpochMilli();

                            List<String> keys = List.of(auctionHashKey, "auctions:upcoming");

                            // Build the ARGV array for Lua
                            List<String> args = new ArrayList<>();
                            args.add(auctionIdStr);
                            args.add(String.valueOf(startTimeEpoch));

                            // Flatten the Map into [key1, value1, key2, value2...]
                            Map<String, String> auctionHash = auctionQueryService.mapDtoToHash(auction);
                            for (Map.Entry<String, String> entry : auctionHash.entrySet()) {
                                args.add(entry.getKey());
                                args.add(entry.getValue());
                            }
                            // Queue the script execution
                            stringOps.execute(hydrateAuctionScript, keys, args.toArray());
                        }
                        return null; // Spring Data Redis requirement
                    }
                });

                // 2. Process the pipeline results to count new hydrations
                int newHydrationCount = 0;
                for (Object result : scriptResults) {
                    if (Long.valueOf(1).equals(result)) {
                        newHydrationCount++;
                    }
                }

                if (newHydrationCount > 0) {
                    log.info("[HYDRATION] Leader {} pipelined {} new upcoming auctions to Redis.", nodeId, newHydrationCount);
                }
            }

        } catch (Exception e) {
            log.error("[HYDRATION ERROR] Failed rolling hydration sweep for leader {}", nodeId, e);
        }
    }
}
