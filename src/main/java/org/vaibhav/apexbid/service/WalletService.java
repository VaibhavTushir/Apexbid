package org.vaibhav.apexbid.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vaibhav.apexbid.config.RedisKeysConfig;
import org.vaibhav.apexbid.entity.User;
import org.vaibhav.apexbid.entity.Wallet;
import org.vaibhav.apexbid.repository.WalletRepository;

import java.util.Map;

@Slf4j
@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final StringRedisTemplate redisTemplate;
    private final RedisKeysConfig redisKeys;

    public WalletService(WalletRepository walletRepository, StringRedisTemplate redisTemplate, RedisKeysConfig redisKeys) {
        this.walletRepository = walletRepository;
        this.redisTemplate = redisTemplate;
        this.redisKeys = redisKeys;
    }

    /**
     * Creates a fresh DB wallet record and attempts to populate Redis.
     * Note: Redis hydration is best-effort. The database remains the source of truth.
     */
    @Transactional
    public void initializeNewWallet(User user) {
        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setBalance(1000000L); // Sign-up bonus: $10,000.00 in cents
        walletRepository.save(wallet);

        try {
            String redisKey = redisKeys.getWalletHashPrefix() + user.getId();
            redisTemplate.opsForHash().putAll(redisKey, Map.of(
                    "balance", String.valueOf(wallet.getBalance()),
                    "locked", "0"
            ));
            log.info("[WALLET] Initialized and hydrated fresh wallet for userId: {}", user.getId());
        } catch (Exception e) {
            // Log the error but do not crash the user registration transaction
            log.warn("[WALLET] DB wallet created for userId: {} but Redis hydration failed. It will be hydrated on demand later. Reason: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * Safely attempts to hydrate a wallet to Redis.
     * Returns true if successful, false if the wallet doesn't exist or Redis is unreachable.
     */
    public boolean hydrateWalletToRedis(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId);
        if (wallet == null) {
            return false;
        }

        try {
            String redisKey = redisKeys.getWalletHashPrefix() + userId;
            Map<String, String> walletData = Map.of(
                    "balance", String.valueOf(wallet.getBalance()),
                    "locked", "0"
            );

            redisTemplate.opsForHash().putAll(redisKey, walletData);
            log.info("[WALLET-HYDRATE] Successfully restored userId: {} wallet snapshot to Redis.", userId);
            return true;
        } catch (Exception e) {
            log.error("[WALLET-HYDRATE] Redis operation failed while hydrating wallet for userId: {}. Reason: {}", userId, e.getMessage());
            return false; // Contract maintained: gracefully return false on infrastructure failure
        }
    }
}