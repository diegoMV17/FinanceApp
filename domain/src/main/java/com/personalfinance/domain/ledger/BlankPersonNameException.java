package com.personalfinance.domain.ledger;

/**
 * A person with no name cannot be picked from a list, which is the only thing
 * the directory is for.
 */
public final class BlankPersonNameException extends LedgerException {

    public BlankPersonNameException() {
        super("A person needs a name");
    }
}
