package org.vaibhav.apexbid.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.vaibhav.apexbid.entity.Transaction;
import org.vaibhav.apexbid.enums.TransactionType;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("SELECT t FROM Transaction t WHERE t.sender.id = :userId OR t.receiver.id = :userId ORDER BY t.createdAt DESC")
    List<Transaction> findWalletHistory(@Param("userId") Long userId);

    // FIXED: Added the underscore to navigate the nested Auction relationship
    Optional<Transaction> findByAuction_IdAndTransactionType(Long auctionId, TransactionType transactionType);
}