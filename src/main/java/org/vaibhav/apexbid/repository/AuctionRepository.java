package org.vaibhav.apexbid.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vaibhav.apexbid.entity.Auction;
import org.vaibhav.apexbid.enums.AuctionStatus;

import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {
    Optional<Auction> findByProductId(Long productId);

    List<Auction> findBySellerId(Long sellerId);

    List<Auction> findByWinnerId(Long winnerId);

    List<Auction> findByStatusAndStartTimeMsLessThanEqual(AuctionStatus status, Long bufferTimeMs);
}
