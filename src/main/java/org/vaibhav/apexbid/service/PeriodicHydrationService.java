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
public class PeriodicHydrationService {
    private final StringRedisTemplate stringRedisTemplate;
    private final AuctionRepository auctionRepository;
    private final AuctionDtoRedisMapper auctionDtoRedisMapper;

    public PeriodicHydrationService(StringRedisTemplate stringRedisTemplate, AuctionRepository auctionRepository, AuctionDtoRedisMapper auctionDtoRedisMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.auctionRepository = auctionRepository;
        this.auctionDtoRedisMapper = auctionDtoRedisMapper;
    }

    public void runRollingHydration(String nodeId, String lockKey) {
        if (!nodeId.equals(stringRedisTemplate.opsForValue().get(lockKey))) {
            return;
        }
        try {
            // 1-Hour lookahead window
            List<AuctionRedis> upcomingAuctions = auctionRepository.findAuctionsByStatusAndStartTimeLessThanEqual(List.of(AuctionStatus.UPCOMING),
                    Instant.now().plusSeconds(3600));
            int newHydrationCount = 0;
            for (AuctionRedis auction : upcomingAuctions) {
                String auctionIdString = auction.id().toString();
                String auctionHashKey = "auction:" + auctionIdString;
                if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(auctionHashKey))) {
                    Map<String, String> auctionHash = auctionDtoRedisMapper.toRedisHash(auction);
                    stringRedisTemplate.opsForHash().putAll(auctionHashKey, auctionHash);
                    long startTimeEpoch = auction.startTime().toEpochMilli();
                    stringRedisTemplate.opsForZSet().add("auctions:upcoming", auctionIdString, startTimeEpoch);
                    newHydrationCount++;
                }
            }
            if (newHydrationCount > 0) {
                log.info("[HYDRATION] Leader {} pushed {} new upcoming auctions.", nodeId, newHydrationCount);
            }

        } catch (Exception e) {
            log.error("[HYDRATION ERROR] Failed rolling hydration sweep", e);
        }
    }
}
