package org.vaibhav.apexbid.enums;

public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    ESCROW_TRANSFER,   // Locked amount transferred from winner to seller
    FINAL_SETTLEMENT  // Remaining balance transferred from winner to seller
}
