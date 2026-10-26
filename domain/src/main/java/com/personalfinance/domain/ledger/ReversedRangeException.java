package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;

/**
 * A range was asked for with its two ends the wrong way round.
 *
 * <p>Refusing it is the point. A range from the 31st to the 1st matches nothing,
 * so the book could answer it with an empty list and be arithmetically right —
 * and that is exactly the failure worth avoiding, because an empty report reads
 * as "you spent nothing in March" when what happened is that the two dates were
 * swapped. Saying so costs one exception; not saying so costs a wrong answer
 * that looks like a right one.
 *
 * <p>Never an {@link IllegalArgumentException}: that one reads as "the caller
 * broke the contract" and comes back as a 500, the server taking the blame for
 * something the book simply does not accept.
 */
public final class ReversedRangeException extends LedgerException {

    private ReversedRangeException(String message) {
        super(message);
    }

    public static ReversedRangeException ofDates(LocalDate from, LocalDate to) {
        return new ReversedRangeException(
                "The date range %s..%s is reversed".formatted(from, to));
    }

    public static ReversedRangeException ofAmounts(Money minimum, Money maximum) {
        return new ReversedRangeException(
                "The value range %s..%s is reversed".formatted(minimum, maximum));
    }
}
