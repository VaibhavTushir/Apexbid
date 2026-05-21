package org.vaibhav.apexbid.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.vaibhav.apexbid.enums.AuctionType;

import java.time.Instant;

public record CreateAuctionRequest(
        //Auction
        @NotBlank()
        String title,
        @NotNull()
        AuctionType auctionType,
        @NotNull()
        Long startPrice,
        @NotNull()
        Instant startTime,
        @NotNull()
        Instant endTime,
        //Product
        @NotBlank()
        @Size(max = 255)
        String productName,
        String productDescription,
        String productImageUrl,
        @NotBlank
        @Size(max = 500, message = "Secret cannot exceed 500 characters")
        String productSecret
) {
}
