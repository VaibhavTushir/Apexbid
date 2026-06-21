package org.vaibhav.apexbid.service;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.vaibhav.apexbid.config.RedisKeysConfig;

import java.util.Map;

@Slf4j
@Service
public class RedisMessageSubscriber implements MessageListener {
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final RedisKeysConfig redisKeys;

    public RedisMessageSubscriber(SimpMessagingTemplate messagingTemplate,
                                  ObjectMapper objectMapper,
                                  RedisKeysConfig redisKeys) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.redisKeys = redisKeys;
    }

    @Override
    public void onMessage(@NonNull Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            String payload = new String(message.getBody());
            Map<String, Object> updateData = objectMapper.readValue(payload, new TypeReference<>() {
            });

            // Replaced hardcoded channels with centralized config
            if (redisKeys.getChannelAuctionUpdates().equals(channel)) {
                // Public Routing: Broadcast to everyone looking at this specific auction
                String auctionId = String.valueOf(updateData.get("auctionId"));
                messagingTemplate.convertAndSend("/topic/auction/" + auctionId, (Object) updateData);
                log.debug("Broadcasted auction {} update", auctionId);

            } else if (redisKeys.getChannelWalletUpdates().equals(channel)) {
                // Private Routing: Send only to the overtaker/outbid user's active session
                String userId = String.valueOf(updateData.get("userId"));
                messagingTemplate.convertAndSendToUser(userId, "/queue/wallet", updateData);
                log.debug("Broadcasted wallet update to user {}", userId);
            }

        } catch (Exception e) {
            log.error("Failed to route Redis message to WebSocket: {}", e.getMessage());
        }
    }
}