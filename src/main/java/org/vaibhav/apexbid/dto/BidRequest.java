package org.vaibhav.apexbid.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BidRequest(
        @NotNull
        Long auctionId,
        @NotNull(message = "Bid amount cannot be null")
        @Min(value = 100, message = "Bid amount must be at least 100 cents")
        Long amount
) {
}
