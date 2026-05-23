package org.vaibhav.apexbid.controller;

import jakarta.validation.Valid;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.vaibhav.apexbid.dto.BidRequest;
import org.vaibhav.apexbid.security.AuthenticatedUser;
import org.vaibhav.apexbid.service.WalletService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bids")
public class BidController {
    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> placeBidScript;
    private final WalletService walletService;

    public BidController(
            StringRedisTemplate stringRedisTemplate,
            DefaultRedisScript<Long> placeBidScript,
            WalletService walletService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.placeBidScript = placeBidScript;
        this.walletService = walletService;
    }

    @PostMapping
    public ResponseEntity<?> placeBid(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody BidRequest request) {
        List<String> keys = List.of(
                "auction:" + request.auctionId(),
                "wallet:" + user.id(),
                "auctions:highest_bids",
                "auctions:most_active",
                "auctions:active"
        );
        Long result = executeBidScript(keys, request, user);
        // Cache-Miss Fallback
        //If redis is cleared, or if we create a worker/TTL to remove 0 locked balance wallets
        if (result != null && result == -4) {
            if (!walletService.hydrateWalletToRedis(user.id())) {
                return error(HttpStatus.INTERNAL_SERVER_ERROR, "Critical Error! Failed to hydrate wallet data.");
            }
            result = executeBidScript(keys, request, user);
        }
        if (result == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Bid placement failed."));
        }
        return switch (result.intValue()) {
            case 1 -> ResponseEntity.accepted().body(Map.of("message", "Bid placed successfully"));
            case -1 -> error(HttpStatus.BAD_REQUEST, "Auction is not active.");
            case -2 -> error(HttpStatus.BAD_REQUEST, "Auction has ended.");
            case -3 -> error(HttpStatus.BAD_REQUEST, "Bid must meet the dynamic minimum threshold.");
            case -4 -> error(HttpStatus.SERVICE_UNAVAILABLE, "Wallet hydration failed.");
            case -5 -> error(HttpStatus.BAD_REQUEST, "Insufficient funds for escrow.");
            case -6 -> error(HttpStatus.FORBIDDEN, "Sellers cannot bid on their own auctions.");
            default -> error(HttpStatus.INTERNAL_SERVER_ERROR, "Unknown error code: " + result);
        };
    }

    private Long executeBidScript(List<String> keys, BidRequest request, AuthenticatedUser user) {
        return stringRedisTemplate.execute(
                placeBidScript,
                keys,
                String.valueOf(request.auctionId()),
                String.valueOf(user.id()),
                user.username(),
                String.valueOf(request.amount()),

                //Raw form of Instant.now().toEpochMilli()
                //Faster and lighter, because it directly calls the OS clock without creating objects
                String.valueOf(System.currentTimeMillis())
        );
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

}
