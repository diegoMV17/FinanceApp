package com.personalfinance.domain.ledger.port;

import com.personalfinance.domain.ledger.Cancellation;
import com.personalfinance.domain.ledger.Chart;
import com.personalfinance.domain.ledger.Ledger;
import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.TransactionId;
import java.util.List;
import java.util.Optional;

/**
 * Where transactions are kept between runs.
 *
 * <p>Entries are written once, with their transaction, and never touched
 * again. The only change a stored transaction ever undergoes is being
 * cancelled, so that is the only update this port offers.
 *
 * <p>The store enforces the same rules as the {@link Ledger}: recording an id
 * twice and cancelling twice both fail, with the ledger's own exceptions. The
 * book in memory and the one in storage can drift apart — another device, a
 * retried request — so neither may assume the other already checked.
 *
 * <p>Reading takes the {@link Chart} because an entry stores only the id of its
 * account. Resolving that id against the one catalogue is what guarantees a
 * loaded entry points at the same {@code Account} instance as everything else;
 * a store that built its own accounts would split a balance in two.
 */
public interface TransactionStore {

    /** @throws com.personalfinance.domain.ledger.DuplicateTransactionException if the id is taken */
    void record(Transaction transaction);

    /**
     * @throws com.personalfinance.domain.ledger.TransactionAlreadyCancelledException
     *         if it was cancelled before; the first cancellation is what stays
     */
    void markCancelled(TransactionId id, Cancellation cancellation);

    /**
     * A correction, stored all at once: the original stops counting and its
     * replacement starts, or neither happens.
     *
     * <p>Done as {@link #markCancelled} followed by {@link #record}, a failure
     * between the two would leave the original cancelled and nothing in its
     * place — the movement would simply vanish from every balance.
     *
     * @param replacement what {@link Ledger#amend} returned; it names the
     *                    original through {@link Transaction#replaces()}
     * @param ofOriginal  how the original was cancelled
     */
    void amend(Transaction replacement, Cancellation ofOriginal);

    Optional<Transaction> find(TransactionId id, Chart chart);

    /** Everything ever recorded, cancelled included, in the order it was recorded. */
    List<Transaction> all(Chart chart);
}
