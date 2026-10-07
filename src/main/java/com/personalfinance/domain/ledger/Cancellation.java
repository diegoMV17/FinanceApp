package com.personalfinance.domain.ledger;

import java.time.Instant;
import java.util.Objects;

/**
 * Why and when a transaction stopped counting.
 *
 * <p>A cancellation is recorded, never a deletion. The row stays in the book
 * because "this was here and then it was taken out" is itself information you
 * will want six months from now.
 */
public record Cancellation(Instant at, String reason, TransactionSource source) {

    public Cancellation {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(source, "source");
        reason = reason == null ? "" : reason.trim();
    }

    static Cancellation because(Instant at, String reason) {
        return new Cancellation(at, reason, TransactionSource.APP);
    }

    /** The reason used when a correction takes the place of an earlier version. */
    static Cancellation supersededBy(Instant at, TransactionId replacement) {
        return new Cancellation(at, "Reemplazada por " + replacement, TransactionSource.APP);
    }
}
