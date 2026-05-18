package org.vaibhav.apexbid.mapper;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.vaibhav.apexbid.entity.Auction;

import java.util.HashMap;
import java.util.Map;

@Component
public class AuctionRedisMapper {
    public @NonNull Map<String, String> toRedisHash(Auction auction) {
        String auctionIdString = auction.getId().toString();
        Map<String, String> fields = new HashMap<>();
        fields.put("id", auctionIdString);
        fields.put("title", auction.getTitle());
        fields.put("status", auction.getStatus().name());
        fields.put("start_price", String.valueOf(auction.getStartPrice()));
        fields.put("winning_bid", String.valueOf(auction.getStartPrice()));
        fields.put("product_id", String.valueOf(auction.getProduct().getId()));
        fields.put("seller_id", String.valueOf(auction.getSeller().getId()));
        fields.put("seller_username", auction.getSeller().getUsername());
        fields.put("winner_id", "");
        fields.put("winner_name", "");
        fields.put("start_time", String.valueOf(auction.getStartTime().toEpochMilli()));
        fields.put("end_time", String.valueOf(auction.getEndTime().toEpochMilli()));
        return fields;
    }
}
