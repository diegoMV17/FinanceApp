package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;
import java.time.Instant;

/**
 * Named accounts for tests, so the tests read like the user stories rather than
 * like setup code.
 */
final class TestAccounts {

    static Account bancolombia() {
        return Account.open(AccountType.ASSET, "Bancolombia");
    }

    static Account cash() {
        return Account.open(AccountType.ASSET, "Efectivo");
    }

    static Account nequi() {
        return Account.open(AccountType.ASSET, "Nequi");
    }

    static Account food() {
        return Account.open(AccountType.EXPENSE, "Alimentación");
    }

    static Account salary() {
        return Account.open(AccountType.INCOME, "Salario");
    }

    static Account owedByJuan() {
        return Account.open(AccountType.RECEIVABLE, "Juan");
    }

    static Account owedToBrother() {
        return Account.open(AccountType.PAYABLE, "Hermano");
    }

    static Account momsFund() {
        return Account.open(AccountType.THIRD_PARTY_FUND, "Mamá");
    }

    static Account openingBalances() {
        return Account.open(AccountType.EQUITY, "Saldos iniciales");
    }

    static Account archived(Account account) {
        account.archive(Money.ZERO, Instant.now());
        return account;
    }

    private TestAccounts() {
    }
}
