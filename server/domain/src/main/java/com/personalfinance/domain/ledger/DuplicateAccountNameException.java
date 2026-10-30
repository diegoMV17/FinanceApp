package com.personalfinance.domain.ledger;

/**
 * Two open accounts of the same kind cannot share a name: picking "Nequi" from
 * a list that holds it twice is a coin flip, and the wrong half of the time the
 * money lands in the wrong place.
 *
 * <p>An archived account may share a name with a live one. "Netflix" retired
 * last year should not stop you opening "Netflix" again today.
 */
public final class DuplicateAccountNameException extends LedgerException {

    private final String name;
    private final AccountType type;

    public DuplicateAccountNameException(AccountType type, String name) {
        super("An open %s account called \"%s\" already exists".formatted(type, name));
        this.type = type;
        this.name = name;
    }

    public String accountName() {
        return name;
    }

    public AccountType type() {
        return type;
    }
}
