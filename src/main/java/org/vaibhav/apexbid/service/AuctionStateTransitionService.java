package org.vaibhav.apexbid.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    public AuctionStateTransitionService(StringRedisTemplate stringRedisTemplate, AuctionRepository auctionRepository) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.auctionRepository = auctionRepository;
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
                    String hashKey = "auction:" + auctionId;
                    String endTimeStr = (String) stringRedisTemplate.opsForHash().get(hashKey, "end_time");
                    String startPrice = (String) stringRedisTemplate.opsForHash().get(hashKey, "start_price");
                    if (endTimeStr != null && startPrice != null) {
                        long endTimeEpoch = Long.parseLong(endTimeStr);

                        stringRedisTemplate.opsForHash().put(hashKey, "status", "ACTIVE");
                        stringRedisTemplate.opsForZSet().remove("auctions:upcoming", auctionId);
                        stringRedisTemplate.opsForZSet().add("auctions:active", auctionId, endTimeEpoch);

                        stringRedisTemplate.opsForZSet().add("auctions:highest_bids", auctionId, Long.parseLong(startPrice));
                        stringRedisTemplate.opsForZSet().add("auctions:most_active", auctionId, 0);

                        auctionIdsToActivate.add(Long.parseLong(auctionId));
                        log.info("[LIFECYCLE] Auction {} went LIVE in Redis.", auctionId);
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
                    String hashKey = "auction:" + auctionId;

                    //1.push to settlement queue
                    stringRedisTemplate.opsForList().leftPush("queue:settlement", auctionId);

                    //2. change status to payment_pending
                    stringRedisTemplate.opsForHash().put(hashKey, "status", "PAYMENT_PENDING");

                    //3. remove from active, and secondary indexes
                    stringRedisTemplate.opsForZSet().remove("auctions:active", auctionId);
                    stringRedisTemplate.opsForZSet().remove("auctions:highest_bids", auctionId);
                    stringRedisTemplate.opsForZSet().remove("auctions:most_active", auctionId);

                    log.info("[LIFECYCLE] Auction {} expired. Safely committed to settlement queue.", auctionId);
                }
            }
        } catch (Exception e) {
            log.error("[LIFECYCLE ERROR] Settlement queuing sweep failed for leader {}", nodeId, e);
        }
    }
}

