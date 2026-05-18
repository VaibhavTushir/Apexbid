package org.vaibhav.apexbid.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.vaibhav.apexbid.entity.Auction;
import org.vaibhav.apexbid.enums.AuctionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {
    Optional<Auction> findByProductId(Long productId);

    List<Auction> findBySellerId(Long sellerId);

    List<Auction> findByWinnerId(Long winnerId);

    List<Auction> findByStatusAndStartTimeLessThanEqual(AuctionStatus status, Instant cutoffTime);

    @Query("SELECT a FROM Auction a WHERE a.status = 'ACTIVE' " + "OR (a.status = 'UPCOMING' AND a.startTime <= :cutoffTime)")
    List<Auction> findAuctionsForInitialization(@Param("cutoffTime") Instant cutoffTime);

    @Modifying
    @Transactional
    @Query("UPDATE Auction a SET a.status = 'ACTIVE' WHERE a.id IN :ids")
    void bulkActivateAuctions(@Param("ids") List<Long> ids);
}
