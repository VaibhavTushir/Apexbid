package org.vaibhav.apexbid.mapper;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.vaibhav.apexbid.dto.AuctionRedis;

import java.util.HashMap;
import java.util.Map;

@Component
public class AuctionDtoRedisMapper {

    public @NonNull Map<String, String> toRedisHash(AuctionRedis auction) {
        Map<String, String> fields = new HashMap<>();

        fields.put("id", auction.id().toString());
        fields.put("title", auction.title());
        fields.put("auction_type", auction.auctionType().name());
        fields.put("status", auction.status().name());
        fields.put("start_price", String.valueOf(auction.startPrice()));
        fields.put("winning_bid", "");
        fields.put("product_id", String.valueOf(auction.productId()));
        fields.put("seller_id", String.valueOf(auction.sellerId()));
        fields.put("seller_username", auction.sellerUsername());
        fields.put("winner_id", "");
        fields.put("winner_name", "");
        fields.put("start_time", String.valueOf(auction.startTime().toEpochMilli()));
        fields.put("end_time", String.valueOf(auction.endTime().toEpochMilli()));

        return fields;
    }
}