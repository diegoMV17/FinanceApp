package com.personalfinance.domain.ledger;

/**
 * An account was opened, renamed or loaded with a name that is nothing but
 * blanks.
 *
 * <p>Length and control characters are {@link Text}'s business; blankness is
 * not, because a description may be empty and an account name may not. An
 * account with no name cannot be told apart from the next one in a list, and
 * the uniqueness rule — one open name per type — would treat every blank one as
 * the same account.
 */
public final class BlankAccountNameException extends LedgerException {

    public BlankAccountNameException() {
        super("An account needs a name");
    }
}
