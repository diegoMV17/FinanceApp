package com.personalfinance.domain.ledger;

/** Where a transaction came from. Matters when the bot misreads a message. */
public enum TransactionSource {
    APP,
    BOT,
    RECURRING,
    SYSTEM
}
