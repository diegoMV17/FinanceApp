package com.personalfinance.domain.ledger;

import static com.personalfinance.domain.ledger.BalanceNature.CREDIT;
import static com.personalfinance.domain.ledger.BalanceNature.DEBIT;

/**
 * Every pocket of money in the ledger: real accounts, people, and categories.
 *
 * <p>Treating a category as an account is what lets one mechanism cover an
 * expense, a transfer, a loan and a third-party fund without a single branch.
 */
public enum AccountType {

    /** Bancolombia, Nequi, cash: money you actually hold. */
    ASSET(DEBIT, true),

    /** Someone owes you (you lent money out). */
    RECEIVABLE(DEBIT, true),

    /** You owe someone. */
    PAYABLE(CREDIT, true),

    /** Money someone handed you to keep or manage. Not yours. */
    THIRD_PARTY_FUND(CREDIT, true),

    /** Income category: salary, sales. */
    INCOME(CREDIT, false),

    /** Expense category: food, transport. */
    EXPENSE(DEBIT, false),

    /** Opening balances and reconciliation adjustments. */
    EQUITY(CREDIT, false);

    private final BalanceNature nature;
    private final boolean holdsMoney;

    AccountType(BalanceNature nature, boolean holdsMoney) {
        this.nature = nature;
        this.holdsMoney = holdsMoney;
    }

    public BalanceNature nature() {
        return nature;
    }

    /**
     * True when the account represents a real stock of money rather than a flow.
     * Archiving one of these with a non-zero balance would make money disappear,
     * so it is forbidden; a category can be archived with history and no harm.
     */
    public boolean requiresZeroBalanceToArchive() {
        return holdsMoney;
    }

    /** True for the types that belong to a person (used from increment 2 on). */
    public boolean belongsToAPerson() {
        return this == RECEIVABLE || this == PAYABLE || this == THIRD_PARTY_FUND;
    }
}
