package org.vaibhav.apexbid.cluster;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.vaibhav.apexbid.service.AuctionStateTransitionService;
import org.vaibhav.apexbid.service.PeriodicHydrationService;
import org.vaibhav.apexbid.service.SystemInitializationService;

import java.time.Duration;

@Slf4j
@Component
@EnableScheduling
public class ClusterCoordinator {
    private final StringRedisTemplate stringRedisTemplate;
    private final SystemInitializationService systemInitializationService;
    private final AuctionStateTransitionService auctionStateTransitionService;
    private final PeriodicHydrationService hydrationService;

    String stateKey = "system:state";
    String lockKey = "lock:cluster:coordinator";

    @Value("${NODE_ID:local-node}")
    String nodeId;

    // Local volatile flag to safely coordinate decoupled tasks across threads
    private volatile boolean isLeader = false;

    public ClusterCoordinator(StringRedisTemplate stringRedisTemplate,
                              SystemInitializationService systemInitializationService,
                              AuctionStateTransitionService auctionStateTransitionService,
                              PeriodicHydrationService hydrationService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.systemInitializationService = systemInitializationService;
        this.auctionStateTransitionService = auctionStateTransitionService;
        this.hydrationService = hydrationService;
    }

    /**
     * 1. Global Cluster Orchestration Loop (Runs every 5 seconds)
     * Handles failover lock acquisition, lease extension, and cold-start warming.
     */
    @Scheduled(fixedDelay = 10000)
    public void coordinateCluster() {
        try {
            // Attempt to acquire the lock if it does not exist (SETNX with 60s TTL)
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, nodeId, Duration.ofSeconds(60));
            if (Boolean.TRUE.equals(acquired)) {
                this.isLeader = true;
                log.info("[LEADER] {} successfully acquired the global cluster lock.", nodeId);
            } else {
                // Lock exists, check if we are the current leader or not
                String currentLeader = stringRedisTemplate.opsForValue().get(lockKey);
                if (nodeId.equals(currentLeader)) {
                    this.isLeader = true;
                    // Extend the lease for another 60 seconds
                    stringRedisTemplate.expire(lockKey, Duration.ofSeconds(60));
                    log.debug("[HEARTBEAT] {} renewed leadership lease for 60 seconds.", nodeId);
                } else {
                    this.isLeader = false;
//                    log.info("[FOLLOWER] {} confirmed leader is {}", nodeId, currentLeader);
                    return;
                }
            }

            // Check Redis State
            String currentState = stringRedisTemplate.opsForValue().get(stateKey);

            // Cold Start
            if (currentState == null || "INITIALIZING".equals(currentState)) {
                log.info("[INITIALIZING] {} is Warming up Redis", nodeId);
                stringRedisTemplate.opsForValue().set(stateKey, "INITIALIZING");
                systemInitializationService.initializeClusterData(nodeId, lockKey);
            }

        } catch (Exception e) {
            this.isLeader = false;
            log.error("[CLUSTER ERROR] Exception caught in coordinator loop for node: {}", nodeId, e);
        }
    }

    /**
     * 2. High-Frequency Auction State Transitions (Runs every 1 second)
     * Handles (Upcoming -> Active) and (Active -> Handoff to queue:settlement).
     */
    @Scheduled(fixedDelay = 1000)
    public void tickAuctionStateTransitions() {
        if (!isLeader) {
            return;
        }

        try {
            String currentState = stringRedisTemplate.opsForValue().get(stateKey);
            if ("READY".equals(currentState)) {
                auctionStateTransitionService.activateUpcomingAuctions(nodeId, lockKey);
                auctionStateTransitionService.queueExpiredAuctions(nodeId, lockKey);
            }
        } catch (Exception e) {
            log.error("[LIFECYCLE ERROR] Exception in leader lifecycle tick for node: {}", nodeId, e);
        }
    }

    /**
     * 3. Rolling Long-Interval Look-Ahead Hydration (Runs every 1 minute)
     * Pulls upcoming database entries into Redis ahead of schedule.
     */
    @Scheduled(fixedDelay = 60000)
    public void tickRollingHydration() {
        if (!isLeader) {
            return;
        }

        try {
            String currentState = stringRedisTemplate.opsForValue().get(stateKey);
            if ("READY".equals(currentState)) {
                hydrationService.runRollingHydration(nodeId, lockKey);
            }
        } catch (Exception e) {
            log.error("[HYDRATION ERROR] Exception in leader hydration tick for node: {}", nodeId, e);
        }
    }
}