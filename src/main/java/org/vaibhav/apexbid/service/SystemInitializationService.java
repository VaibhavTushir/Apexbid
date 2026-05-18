package org.vaibhav.apexbid.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.vaibhav.apexbid.entity.Auction;
import org.vaibhav.apexbid.enums.AuctionStatus;
import org.vaibhav.apexbid.mapper.AuctionRedisMapper;
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
    private final AuctionRedisMapper auctionRedisMapper;

    public SystemInitializationService(StringRedisTemplate stringRedisTemplate, AuctionRepository auctionRepository, AuctionRedisMapper auctionRedisMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.auctionRepository = auctionRepository;
        this.auctionRedisMapper = auctionRedisMapper;
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
            List<Auction> targetAuctions = auctionRepository.findAuctionsForInitialization(Instant.now().plusSeconds(3600));

            for (Auction auction : targetAuctions) {
                String auctionIdString = auction.getId().toString();
                String auctionHashKey = "auction:" + auctionIdString;

                // 1. Hydrate the main auction details mapping
                if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(auctionHashKey))) {
                    Map<String, String> fields = auctionRedisMapper.toRedisHash(auction);
                    stringRedisTemplate.opsForHash().putAll(auctionHashKey, fields);
                }

                // 2. Add to corresponding ZSET
                if (AuctionStatus.UPCOMING == auction.getStatus()) {
                    long startTimeEpoch = auction.getStartTime().toEpochMilli();
                    stringRedisTemplate.opsForZSet().add("auctions:upcoming", auctionIdString, startTimeEpoch);

                } else if (AuctionStatus.ACTIVE == auction.getStatus()) {
                    long endTimeEpoch = auction.getEndTime().toEpochMilli();
                    stringRedisTemplate.opsForZSet().add("auctions:active", auctionIdString, endTimeEpoch);

                    // add to secondary indexes
                    stringRedisTemplate.opsForZSet().add("auctions:highest_bids", auctionIdString, auction.getStartPrice());
                    stringRedisTemplate.opsForZSet().add("auctions:most_active", auctionIdString, 0);
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