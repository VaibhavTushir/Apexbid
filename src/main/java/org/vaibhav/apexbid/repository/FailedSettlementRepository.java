package org.vaibhav.apexbid.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vaibhav.apexbid.entity.FailedSettlement;

import java.util.List;

public interface FailedSettlementRepository extends JpaRepository<FailedSettlement, Long> {
     List<FailedSettlement> findAllByStatus(String status);
}
