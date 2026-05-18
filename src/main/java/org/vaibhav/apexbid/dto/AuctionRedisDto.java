package org.vaibhav.apexbid.dto;

import org.vaibhav.apexbid.enums.AuctionStatus;
import org.vaibhav.apexbid.enums.AuctionType;

import java.time.Instant;

public record AuctionRedisDto(
        Long id,
        String title,
        AuctionType auctionType,
        AuctionStatus status,
        Long startPrice,
        Long winningBid,
        Long productId,
        Long sellerId,
        String sellerUsername,
        Instant startTime,
        Instant endTime
) {}