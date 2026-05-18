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
public class PeriodicHydrationService {
    private final StringRedisTemplate stringRedisTemplate;
    private final AuctionRepository auctionRepository;
    private final AuctionRedisMapper auctionRedisMapper;

    public PeriodicHydrationService(StringRedisTemplate stringRedisTemplate, AuctionRepository auctionRepository, AuctionRedisMapper auctionRedisMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.auctionRepository = auctionRepository;
        this.auctionRedisMapper = auctionRedisMapper;
    }

    public void runRollingHydration(String nodeId, String lockKey) {
        if (!nodeId.equals(stringRedisTemplate.opsForValue().get(lockKey))) {
            return;
        }
        try {
            // 1-Hour lookahead window
            Instant lookAheadCutoff = Instant.now().plusSeconds(3600);
            List<Auction> upcomingAuctions = auctionRepository.findByStatusAndStartTimeLessThanEqual(AuctionStatus.UPCOMING, lookAheadCutoff);
            int newHydrationCount = 0;
            for (Auction auction : upcomingAuctions) {
                String auctionIdString = auction.getId().toString();
                String auctionHashKey = "auction:" + auctionIdString;
                if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(auctionHashKey))) {
                    Map<String, String> auctionHash = auctionRedisMapper.toRedisHash(auction);
                    stringRedisTemplate.opsForHash().putAll(auctionHashKey, auctionHash);
                    long startTimeEpoch = auction.getStartTime().toEpochMilli();
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
