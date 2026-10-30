package com.personalfinance.domain.ledger;

/**
 * Only the three kinds of account that <em>are</em> somebody can belong to a
 * person: what they owe you, what you owe them, and the money of theirs you
 * hold. See {@link AccountType#belongsToAPerson()}.
 *
 * <p>A bank account is not a person, and a category even less so. Letting
 * "Alimentación" belong to Juan would make his position include an expense of
 * yours, which is the one number the whole app exists to keep separate. The
 * CHECK in the V1 refuses the same thing outside the application.
 */
public final class AccountCannotBelongToAPersonException extends LedgerException {

    private final Account account;

    public AccountCannotBelongToAPersonException(Account account) {
        super("A %s account like %s does not belong to a person"
                .formatted(account.type(), account.name()));
        this.account = account;
    }

    public Account account() {
        return account;
    }
}
