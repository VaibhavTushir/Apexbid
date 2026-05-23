package org.vaibhav.apexbid.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.vaibhav.apexbid.entity.Wallet;
import org.vaibhav.apexbid.repository.WalletRepository;

import java.util.Map;

@Slf4j
@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final StringRedisTemplate redisTemplate;

    public WalletService(WalletRepository walletRepository, StringRedisTemplate redisTemplate) {
        this.walletRepository = walletRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Fetches wallet from PostgreSQL and loads it into Redis.
     * Returns true if successful, false if the user has no wallet in the DB.
     */
    public boolean hydrateWalletToRedis(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId);

        if (wallet == null) {
            // Hypothetical Scenario: No wallet found
            return false;
        }


        String redisKey = "wallet:" + userId;

        try {

            Map<String, String> walletData = Map.of(
                    "balance", String.valueOf(wallet.getBalance()),
                    "locked", "0"
            );
            redisTemplate.opsForHash().putAll(redisKey, walletData);
            return true;

        } catch (Exception e) {
            log.error("Error hydrating wallet to Redis for userId {}: {}", userId, e.getMessage());
            return false;
        }
    }
}