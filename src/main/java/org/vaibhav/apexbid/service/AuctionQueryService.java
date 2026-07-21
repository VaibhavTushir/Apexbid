package org.vaibhav.apexbid.service;

import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.vaibhav.apexbid.config.RedisKeysConfig;
import org.vaibhav.apexbid.dto.AuctionRedis;
import org.vaibhav.apexbid.entity.Auction;
import org.vaibhav.apexbid.enums.AuctionStatus;
import org.vaibhav.apexbid.enums.AuctionType;

import java.time.Instant;
import java.util.*;

@Service
public class AuctionQueryService {
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisKeysConfig redisKeys;

    public AuctionQueryService(StringRedisTemplate stringRedisTemplate, RedisKeysConfig redisKeys) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisKeys = redisKeys;
    }

    // 1. ACTIVE (Ending soonest first, Ascending)
    public List<AuctionRedis> getActiveAuctions(int page, int size) {
        Set<String> ids = stringRedisTemplate.opsForZSet().range(redisKeys.getAuctionsActive(), getStart(page, size), getEnd(page, size));
        return fetchAuctionsFromRedisByIds(ids);
    }

    // 2. UPCOMING (Starting soonest first, Ascending)
    public List<AuctionRedis> getUpcomingAuctions(int page, int size) {
        Set<String> ids = stringRedisTemplate.opsForZSet().range(redisKeys.getAuctionsUpcoming(), getStart(page, size), getEnd(page, size));
        return fetchAuctionsFromRedisByIds(ids);
    }

    // 3. HIGHEST BIDS (Highest price first, Descending / Reverse)
    public List<AuctionRedis> getHighestBidAuctions(int page, int size) {
        Set<String> ids = stringRedisTemplate.opsForZSet().reverseRange(redisKeys.getAuctionsHighestBids(), getStart(page, size), getEnd(page, size));
        return fetchAuctionsFromRedisByIds(ids);
    }

    // 4. MOST ACTIVE (Highest activity count first, Descending / Reverse)
    public List<AuctionRedis> getTrendingAuctions(int page, int size) {
        Set<String> ids = stringRedisTemplate.opsForZSet().reverseRange(redisKeys.getAuctionsMostActive(), getStart(page, size), getEnd(page, size));
        return fetchAuctionsFromRedisByIds(ids);
    }

    public List<AuctionRedis> getSellerAuctionsEnriched(List<Auction> dbAuctions) {
        List<AuctionRedis> finalResponse = new ArrayList<>();
        Set<String> activeIdsToFetch = new HashSet<>();

        // 1. Separate Active from Non-Active
        for (Auction a : dbAuctions) {
            if (a.getStatus() == AuctionStatus.ACTIVE) {
                // Queue this ID to fetch the live state from Redis
                activeIdsToFetch.add(a.getId().toString());
            } else {
                // DB is the source of truth for Upcoming/Ended. Map it directly.
                finalResponse.add(new AuctionRedis(
                        a.getId(), a.getTitle(), a.getAuctionType(), a.getStatus(),
                        a.getStartPrice(), a.getWinningBid(), a.getProduct().getId(),
                        a.getSeller().getId(), a.getSeller().getUsername(),
                        a.getWinner() != null ? a.getWinner().getId() : null,
                        a.getWinner() != null ? a.getWinner().getUsername() : null,
                        a.getStartTime(), a.getEndTime()
                ));
            }
        }

        // 2. Fetch all live Active auctions from Redis in ONE network pipeline trip
        if (!activeIdsToFetch.isEmpty()) {
            List<AuctionRedis> liveActiveAuctions = fetchAuctionsFromRedisByIds(activeIdsToFetch);
            finalResponse.addAll(liveActiveAuctions);
        }

        // 3. Sort the final combined list by Start Time (Descending)
        finalResponse.sort(Comparator.comparing(AuctionRedis::startTime).reversed());

        return finalResponse;
    }

    /**
     * Fetches all auctions a user is currently winning from Redis.
     */
    public List<AuctionRedis> getWinningAuctions(Long userId) {
        Set<String> auctionIds = stringRedisTemplate.opsForSet()
                .members(redisKeys.getWinningSetPrefix() + userId);
        return fetchAuctionsFromRedisByIds(auctionIds);
    }

    //Helper Methods
    private long getStart(int page, int size) {
        return (long) page * size;
    }

    private long getEnd(int page, int size) {
        return getStart(page, size) + size - 1;
    }

    private List<AuctionRedis> fetchAuctionsFromRedisByIds(Set<String> auctionIds) {
        if (auctionIds == null || auctionIds.isEmpty()) return Collections.emptyList();

        // Fetch all hashes in one single network trip
        List<Object> results = stringRedisTemplate.executePipelined((RedisConnection connection) -> {
            for (String id : auctionIds) {
                byte[] key = stringRedisTemplate.getStringSerializer().serialize(redisKeys.getAuctionHashPrefix() + id);
                connection.hashCommands().hGetAll(key);
            }
            return null;
        });

        List<AuctionRedis> auctions = new ArrayList<>();
        int i = 0;
        for (String id : auctionIds) {
            @SuppressWarnings("unchecked") Map<Object, Object> hash = (Map<Object, Object>) results.get(i++);
            if (hash != null && !hash.isEmpty()) {
                auctions.add(mapHashToDto(id, hash));
            }
        }
        return auctions;
    }

    private AuctionRedis mapHashToDto(String id, Map<Object, Object> hash) {

        // 1. Safely extract values that might be empty strings ("")
        String winningBidStr = (String) hash.get("winning_bid");
        Long winningBid = (winningBidStr != null && !winningBidStr.isEmpty())
                ? Long.parseLong(winningBidStr) : null;

        String winnerIdStr = (String) hash.get("winner_id");
        Long winnerId = (winnerIdStr != null && !winnerIdStr.isEmpty())
                ? Long.parseLong(winnerIdStr) : null;

        String winnerUsernameStr = (String) hash.get("winner_username");
        String winnerUsername = (winnerUsernameStr != null && !winnerUsernameStr.isEmpty())
                ? winnerUsernameStr : null;

        // 2. Build the DTO
        return new AuctionRedis(
                Long.parseLong(id),
                (String) hash.get("title"),
                AuctionType.valueOf((String) hash.get("auction_type")),
                AuctionStatus.valueOf((String) hash.get("status")),
                Long.parseLong((String) hash.get("start_price")),
                winningBid,
                Long.parseLong((String) hash.get("product_id")),
                Long.parseLong((String) hash.get("seller_id")),
                (String) hash.get("seller_username"),
                winnerId,
                winnerUsername,
                Instant.ofEpochMilli(Long.parseLong((String) hash.get("start_time"))),
                Instant.ofEpochMilli(Long.parseLong((String) hash.get("end_time")))
        );
    }

    public @NonNull Map<String, String> mapDtoToHash(AuctionRedis auction) {
        Map<String, String> fields = new HashMap<>();

        fields.put("id", auction.id().toString());
        fields.put("title", auction.title());
        fields.put("auction_type", auction.auctionType().name());
        fields.put("status", auction.status().name());
        fields.put("start_price", String.valueOf(auction.startPrice()));
        fields.put("product_id", String.valueOf(auction.productId()));
        fields.put("seller_id", String.valueOf(auction.sellerId()));
        fields.put("seller_username", auction.sellerUsername());
        fields.put("start_time", String.valueOf(auction.startTime().toEpochMilli()));
        fields.put("end_time", String.valueOf(auction.endTime().toEpochMilli()));

        // Null-safe handling for fields that might not exist yet
        fields.put("winning_bid", auction.winningBid() != null ? String.valueOf(auction.winningBid()) : "");
        fields.put("winner_id", auction.winnerId() != null ? String.valueOf(auction.winnerId()) : "");
        fields.put("winner_username", auction.winnerUsername() != null ? auction.winnerUsername() : "");

        return fields;
    }
}