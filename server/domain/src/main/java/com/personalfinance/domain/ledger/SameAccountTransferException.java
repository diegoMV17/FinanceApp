package com.personalfinance.domain.ledger;

/**
 * A transfer was asked to move money from an account into itself.
 *
 * <p>It would balance — the two entries cancel out — and that is exactly why it
 * has to be refused here instead of being left to the balance rule: a
 * zero-effect movement recorded in a book where nothing is ever deleted is a
 * row that can only be cancelled, never removed, and it means someone's tap
 * did not do what they thought it did.
 */
public final class SameAccountTransferException extends LedgerException {

    private final Account account;

    public SameAccountTransferException(Account account) {
        super("A transfer needs two different accounts; both ends are %s"
                .formatted(account.name()));
        this.account = account;
    }

    public Account account() {
        return account;
    }
}
