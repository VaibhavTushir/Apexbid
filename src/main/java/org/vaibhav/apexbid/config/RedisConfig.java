package org.vaibhav.apexbid.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.vaibhav.apexbid.service.RedisMessageSubscriber;

@Configuration
public class RedisConfig {

    // 1. PUB/SUB Channels
    @Bean
    public MessageListenerAdapter messageListener(RedisMessageSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber);
    }

    @Bean
    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory,
                                                        MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // Listen to both channels
        container.addMessageListener(listenerAdapter, new ChannelTopic("auction:updates"));
        container.addMessageListener(listenerAdapter, new ChannelTopic("wallet:updates"));

        return container;
    }

    // 2. Lua Script
    //Real-time bidding script
    @Bean
    public DefaultRedisScript<Long> placeBidScript() {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("scripts/place_bid.lua"));
        redisScript.setResultType(Long.class);
        return redisScript;
    }
    @Bean
    public DefaultRedisScript<Long> hydrateAuctionScript() {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("scripts/hydrate_auction.lua"));
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    //Auction state transition scripts

    //Upcoming -> Active
    @Bean
    public DefaultRedisScript<Long> activateAuctionScript() {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("scripts/activate_auction.lua"));
        redisScript.setResultType(Long.class);
        return redisScript;
    }
    //Active ->Settlement Queue
    @Bean
    public DefaultRedisScript<Long> queueSettlementScript() {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("scripts/queue_settlement.lua"));
        redisScript.setResultType(Long.class);
        return redisScript;
    }

}
