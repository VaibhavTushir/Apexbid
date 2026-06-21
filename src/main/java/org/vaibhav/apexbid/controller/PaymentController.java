package org.vaibhav.apexbid.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.vaibhav.apexbid.security.AuthenticatedUser;
import org.vaibhav.apexbid.service.CheckOutService;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final CheckOutService checkoutService;

    @GetMapping("/auctions/{auctionId}/pending-balance")
    public ResponseEntity<Long> getPendingBalance(
            @PathVariable Long auctionId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(checkoutService.getPendingBalance(auctionId, currentUser.id()));
    }

    @PostMapping("/auctions/{auctionId}/checkout")
    public ResponseEntity<String> processCheckout(
            @PathVariable Long auctionId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        checkoutService.initiateCheckout(auctionId, currentUser.id());
        return ResponseEntity.ok("Payment successful. Finalizing settlement.");
    }
}