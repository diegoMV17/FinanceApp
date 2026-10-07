package com.personalfinance.domain.ledger;

/**
 * Archiving twice would overwrite the date it was archived on, which is a fact
 * the history should keep. Same reasoning as cancelling twice.
 */
public final class AccountAlreadyArchivedException extends LedgerException {

    private final Account account;

    public AccountAlreadyArchivedException(Account account) {
        super("%s was already archived on %s"
                .formatted(account.name(), account.archivedAt().orElseThrow()));
        this.account = account;
    }

    public Account account() {
        return account;
    }
}
