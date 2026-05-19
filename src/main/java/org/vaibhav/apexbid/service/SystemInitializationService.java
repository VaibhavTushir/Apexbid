package org.vaibhav.apexbid.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.vaibhav.apexbid.dto.AuctionRedis;
import org.vaibhav.apexbid.enums.AuctionStatus;
import org.vaibhav.apexbid.mapper.AuctionDtoRedisMapper;
import org.vaibhav.apexbid.repository.AuctionRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SystemInitializationService {

    // Non-static final fields cleanly managed by Spring's application context
    private final StringRedisTemplate stringRedisTemplate;
    private final AuctionRepository auctionRepository;
    private final AuctionDtoRedisMapper auctionDtoRedisMapper;

    public SystemInitializationService(StringRedisTemplate stringRedisTemplate, AuctionRepository auctionRepository, AuctionDtoRedisMapper auctionDtoRedisMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.auctionRepository = auctionRepository;
        this.auctionDtoRedisMapper = auctionDtoRedisMapper;
    }


    public void initializeClusterData(String nodeId, String lockKey) {
        // Ensure we still hold the lock before touching the database
        if (!nodeId.equals(stringRedisTemplate.opsForValue().get(lockKey))) {
            log.warn("[INITIALIZATION] Aborted. {} lost leadership before initialization could start.", nodeId);
            return;
        }

        try {
            log.info("[INITIALIZATION] Cold Start. {} is warming up Redis", nodeId);

            // Load all ACTIVE and UPCOMING auctions that should be active within the next 1 hour
            List<AuctionRedis> targetAuctions = auctionRepository.findAuctionsByStatusAndStartTimeLessThanEqual(
                    List.of(AuctionStatus.ACTIVE, AuctionStatus.UPCOMING),
                    Instant.now().plusSeconds(3600)
            );

            for (AuctionRedis auction : targetAuctions) {
                String auctionIdString = auction.id().toString();
                String auctionHashKey = "auction:" + auctionIdString;

                // 1. Hydrate the main auction details mapping
                if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(auctionHashKey))) {
                    Map<String, String> fields = auctionDtoRedisMapper.toRedisHash(auction);
                    stringRedisTemplate.opsForHash().putAll(auctionHashKey, fields);
                }

                // 2. Add to corresponding ZSET
                if (AuctionStatus.UPCOMING == auction.status()) {
                    long startTimeEpoch = auction.startTime().toEpochMilli();
                    stringRedisTemplate.opsForZSet().add("auctions:upcoming", auctionIdString, startTimeEpoch);

                } else if (AuctionStatus.ACTIVE == auction.status()) {
                    {
                        long endTimeEpoch = auction.endTime().toEpochMilli();
                        stringRedisTemplate.opsForZSet().add("auctions:active", auctionIdString, endTimeEpoch);

                        // add to secondary indexes
                        stringRedisTemplate.opsForZSet().add("auctions:highest_bids", auctionIdString, auction.startPrice());
                        stringRedisTemplate.opsForZSet().add("auctions:most_active", auctionIdString, 0);
                    }
                }


            }
            // Master switch flip
            stringRedisTemplate.opsForValue().set("system:state", "READY");
            log.info("[WARMUP-COMPLETE] Redis layer is loaded. System state flipped to READY.");
        } catch (Exception e) {
            log.error("[INITIALIZATION ERROR] Cold start cache warming failed", e);
            // Revert state so the next coordinator can attempt recovery
            stringRedisTemplate.opsForValue().set("system:state", "INITIALIZING");
        }
    }
}