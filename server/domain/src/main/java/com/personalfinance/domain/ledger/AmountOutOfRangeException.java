package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;

/**
 * An entry tried to move more money than the book will ever hold.
 *
 * <p>This limit is there for arithmetic, not for taste. {@link Money} adds with
 * {@link Math#addExact}, so one entry parked near {@code Long.MAX_VALUE} makes
 * every later sum throw — and since nothing is ever deleted, a single
 * mistyped or maliciously parsed movement would break every balance from then
 * on, permanently.
 */
public final class AmountOutOfRangeException extends LedgerException {

    private final Money amount;
    private final long limit;

    public AmountOutOfRangeException(Account account, Money amount, long limitInCents) {
        super("An entry on %s cannot move %s; the limit is %s"
                .formatted(account.name(), amount, Money.ofCents(limitInCents)));
        this.amount = amount;
        this.limit = limitInCents;
    }

    public Money amount() {
        return amount;
    }

    /** The largest absolute amount an entry may carry, in cents. */
    public long limit() {
        return limit;
    }
}
