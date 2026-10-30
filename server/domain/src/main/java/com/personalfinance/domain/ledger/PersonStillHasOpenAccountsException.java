package com.personalfinance.domain.ledger;

import java.util.List;

/**
 * Archiving a person whose accounts are still open would hide a debt rather
 * than settle it. Archive what belongs to them first.
 *
 * <p>There is no balance check here on purpose. An account that holds money
 * already refuses to archive with one, so "every account archived" is the same
 * promise arrived at through the rule that owns it. Asking twice would be a
 * second truth, and the two would drift.
 */
public final class PersonStillHasOpenAccountsException extends LedgerException {

    private final Person person;
    private final List<Account> openAccounts;

    public PersonStillHasOpenAccountsException(Person person, List<Account> openAccounts) {
        super("%s still has %d open account(s); archive them first"
                .formatted(person.name(), openAccounts.size()));
        this.person = person;
        this.openAccounts = List.copyOf(openAccounts);
    }

    public Person person() {
        return person;
    }

    public List<Account> openAccounts() {
        return openAccounts;
    }
}
