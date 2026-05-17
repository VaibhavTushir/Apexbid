package org.vaibhav.apexbid.entity;

import jakarta.persistence.*;
import lombok.*;
import org.vaibhav.apexbid.enums.AuctionStatus;
import org.vaibhav.apexbid.enums.AuctionType;

@Entity
@Table(name = "auctions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Auction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "auction_type", nullable = false, length = 30)
    private AuctionType auctionType; // STANDARD, ANTI_SNIPE

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AuctionStatus status; // UPCOMING, ACTIVE, PAYMENT_PENDING, SETTLED, CANCELLED

    // (Cents)
    @Column(name = "start_price", nullable = false)
    private Long startPrice;

    @Column(name = "winning_bid")
    private Long winningBid; // Current highest bid or final winning price

    // Relational Joins (Foreign Keys)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner; // Nullable until active bids fall or auction settles

    // Timestamps (Epoch Milliseconds)
    @Column(name = "start_time_ms", nullable = false)
    private Long startTimeMs;

    @Column(name = "end_time_ms", nullable = false)
    private Long endTimeMs; // Dynamically extended if ANTI_SNIPE triggers
}