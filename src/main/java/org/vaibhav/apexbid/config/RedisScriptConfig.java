package org.vaibhav.apexbid.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisScriptConfig {
    //Real-time bidding script
    @Bean
    public DefaultRedisScript<String> placeBidScript() {
        DefaultRedisScript<String> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("scripts/place_bid.lua"));
        redisScript.setResultType(String.class);
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
