package org.vaibhav.apexbid.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.vaibhav.apexbid.entity.FailedSettlement;
import org.vaibhav.apexbid.repository.FailedSettlementRepository;

import java.io.PrintWriter;
import java.io.StringWriter;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlqService {

    private final FailedSettlementRepository failedSettlementRepository;

    @Transactional
    public void logFailure(Long auctionId, String phase, Throwable exception) {

        // Extract the true, full stack trace into a String
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        String fullStackTrace = sw.toString();

        FailedSettlement dlqEntry = FailedSettlement.builder()
                .auctionId(auctionId)
                .phase(phase)
                .errorMessage(fullStackTrace)
                .build();

        failedSettlementRepository.save(dlqEntry);
        log.error("CRITICAL: Auction {} failed during {}. Moved to DLQ.", auctionId, phase);
    }
}