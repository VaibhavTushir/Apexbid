package org.vaibhav.apexbid.controller;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.vaibhav.apexbid.dto.AuctionRedis;
import org.vaibhav.apexbid.dto.CreateAuctionRequest;
import org.vaibhav.apexbid.entity.Auction;
import org.vaibhav.apexbid.entity.Product;
import org.vaibhav.apexbid.entity.User;
import org.vaibhav.apexbid.enums.AuctionStatus;
import org.vaibhav.apexbid.repository.AuctionRepository;
import org.vaibhav.apexbid.repository.ProductRepository;
import org.vaibhav.apexbid.dto.AuthenticatedUser;
import org.vaibhav.apexbid.security.SecretEncryptionUtil;
import org.vaibhav.apexbid.service.AuctionQueryService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auctions")
public class AuctionController {
    private final AuctionRepository auctionRepository;
    private final ProductRepository productRepository;
    private final SecretEncryptionUtil secretEncryptionUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final AuctionQueryService auctionQueryService;

    public AuctionController(AuctionRepository auctionRepository,
                             ProductRepository productRepository,
                             SecretEncryptionUtil secretEncryptionUtil,
                             StringRedisTemplate stringRedisTemplate,
                             AuctionQueryService auctionQueryService) {
        this.auctionRepository = auctionRepository;
        this.productRepository = productRepository;
        this.secretEncryptionUtil = secretEncryptionUtil;
        this.stringRedisTemplate = stringRedisTemplate;
        this.auctionQueryService = auctionQueryService;
    }

    //Zero-DB Redis Feeds (Public)
    @GetMapping("/active")
    public ResponseEntity<List<AuctionRedis>> getActiveFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auctionQueryService.getActiveAuctions(page, size));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<AuctionRedis>> getUpcomingFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auctionQueryService.getUpcomingAuctions(page, size));
    }

    @GetMapping("/trending")
    public ResponseEntity<List<AuctionRedis>> getTrendingFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auctionQueryService.getTrendingAuctions(page, size));
    }

    @GetMapping("/highest-bids")
    public ResponseEntity<List<AuctionRedis>> getHighestBidsFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auctionQueryService.getHighestBidAuctions(page, size));
    }

    //Database Queries
    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<List<AuctionRedis>> getAuctionsBySeller(@PathVariable Long sellerId) {
        // Fast lookup for the seller's entire portfolio
        List<Auction> dbAuctions = auctionRepository.findBySellerId(sellerId);

        // Enrich the active ones with live Redis data
        List<AuctionRedis> response = auctionQueryService.getSellerAuctionsEnriched(dbAuctions);

        return ResponseEntity.ok(response);
    }

    //Create auction and product (Transactional)
    @PostMapping
    @Transactional
    public ResponseEntity<?> createAuction(@Valid @RequestBody CreateAuctionRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {

        Instant now = Instant.now();

        // Start time must be in the future
        if (request.startTime().isBefore(now)) {
            return ResponseEntity.badRequest().body("Start time must be in the future");
        }

        // End time must be at least 1 minute after start time
        if (request.endTime().isBefore(request.startTime().plus(Duration.ofMinutes(1)))) {
            return ResponseEntity.badRequest().body("Auction must last at least 1 minute");
        }

        Product product = new Product();
        product.setName(request.productName());
        product.setDescription(request.productDescription());
        product.setImageUrl(request.productImageUrl());
        product.setSecretCode(secretEncryptionUtil.encrypt(request.productSecret()));

        User stubSeller = new User();
        stubSeller.setId(principal.id());
        product.setSeller(stubSeller);

        Product savedProduct = productRepository.save(product);

        Auction auction = new Auction();
        auction.setTitle(request.title());
        auction.setAuctionType(request.auctionType());
        auction.setStatus(AuctionStatus.UPCOMING);
        auction.setStartPrice(request.startPrice());
        auction.setProduct(savedProduct);

        auction.setSeller(stubSeller);
        auction.setStartTime(request.startTime());
        auction.setEndTime(request.endTime());
        auctionRepository.save(auction);

        Instant tenMinutesFromNow = now.plus(Duration.ofMinutes(10));
        //if auction starts within the next 10 minutes, trigger immediate hydration for this auction
        if (request.startTime().isBefore(tenMinutesFromNow)) {
            AuctionRedis auctionRedis = new AuctionRedis(
                    auction.getId(),
                    auction.getTitle(),
                    auction.getAuctionType(),
                    auction.getStatus(),
                    auction.getStartPrice(),
                    null,
                    savedProduct.getId(),
                    principal.id(),
                    principal.username(),
                    null,
                    null,
                    auction.getStartTime(),
                    auction.getEndTime()
            );
            String auctionIdString = auction.getId().toString();
            String auctionHashKey = "auction:" + auctionIdString;
            Map<String, String> auctionHash = auctionQueryService.mapDtoToHash(auctionRedis);
            stringRedisTemplate.opsForHash().putAll(auctionHashKey, auctionHash);
            long startTimeEpoch = auctionRedis.startTime().toEpochMilli();
            stringRedisTemplate.opsForZSet().add("auctions:upcoming", auctionIdString, startTimeEpoch);
            log.info("Auction {} starts in <10 mins. Injected directly into Redis.", auction.getId());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Auction created successfully!"));

    }


}
