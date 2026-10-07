package com.personalfinance.domain.ledger;

import static com.personalfinance.domain.ledger.AccountType.ASSET;
import static com.personalfinance.domain.ledger.AccountType.EQUITY;
import static com.personalfinance.domain.ledger.AccountType.EXPENSE;
import static com.personalfinance.domain.ledger.AccountType.INCOME;
import static com.personalfinance.domain.ledger.AccountType.PAYABLE;
import static com.personalfinance.domain.ledger.AccountType.RECEIVABLE;
import static com.personalfinance.domain.ledger.AccountType.THIRD_PARTY_FUND;

import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import java.util.List;

/**
 * The templates behind the buttons the person actually presses.
 *
 * <p>Every method here builds an ordinary {@link Transaction}. There is no
 * special handling for loans, transfers or third-party money anywhere else in
 * the system: the difference between them lives in which accounts the entries
 * touch, and nothing more. That is the whole point of the design.
 *
 * <p>Callers always pass the amount as a positive number, the way the person
 * says it out loud. Working out the signs is this class's job.
 */
public final class Operations {

    /** HU01. Opening balance, stated the way the person reads it. */
    public static Transaction openingBalance(
            LocalDate date, Account account, Account equity, Money amount) {

        requirePositive("An opening balance", amount);
        requireType(equity, EQUITY);

        Money onTheLedger = amount.times(account.type().nature().presentationSign());
        return Transaction.record(date, "Saldo inicial", List.of(
                Entry.of(account, onTheLedger),
                Entry.of(equity, onTheLedger.negated())));
    }

    /** HU04. Money arrives and it is yours. */
    public static Transaction income(
            LocalDate date, String description,
            Account receivedInto, Account category, Money amount) {

        requirePositive("Income", amount);
        requireType(receivedInto, ASSET);
        requireType(category, INCOME);

        return Transaction.record(date, description, List.of(
                Entry.of(receivedInto, amount),
                Entry.of(category, amount.negated())));
    }

    /** HU05, HU12. Money leaves and it was yours. */
    public static Transaction expense(
            LocalDate date, String description,
            Account paidFrom, Account category, Money amount) {

        requirePositive("An expense", amount);
        requireType(paidFrom, ASSET);
        requireType(category, EXPENSE);

        return Transaction.record(date, description, List.of(
                Entry.of(paidFrom, amount.negated()),
                Entry.of(category, amount)));
    }

    /**
     * HU10. Your own money changes pocket. Touches no income and no expense
     * category, which is exactly what the user story asks for.
     */
    public static Transaction transfer(
            LocalDate date, String description, Account from, Account to, Money amount) {

        requirePositive("A transfer", amount);
        requireType(from, ASSET);
        requireType(to, ASSET);
        if (from.equals(to)) {
            throw new IllegalArgumentException("A transfer needs two different accounts");
        }

        return Transaction.record(date, description, List.of(
                Entry.of(from, amount.negated()),
                Entry.of(to, amount)));
    }

    /** HU13. You lend money out. It left your pocket but it is still yours. */
    public static Transaction lend(
            LocalDate date, String description,
            Account paidFrom, Account borrower, Money amount) {

        requirePositive("A loan", amount);
        requireType(paidFrom, ASSET);
        requireType(borrower, RECEIVABLE);

        return Transaction.record(date, description, List.of(
                Entry.of(paidFrom, amount.negated()),
                Entry.of(borrower, amount)));
    }

    /** HU14. They pay you back, in full or in part. */
    public static Transaction collectLoan(
            LocalDate date, String description,
            Account borrower, Account receivedInto, Money amount) {

        requirePositive("A repayment", amount);
        requireType(borrower, RECEIVABLE);
        requireType(receivedInto, ASSET);

        return Transaction.record(date, description, List.of(
                Entry.of(borrower, amount.negated()),
                Entry.of(receivedInto, amount)));
    }

    /** HU15. Someone lends to you. The money is in your pocket but is not yours. */
    public static Transaction borrow(
            LocalDate date, String description,
            Account lender, Account receivedInto, Money amount) {

        requirePositive("A borrowing", amount);
        requireType(lender, PAYABLE);
        requireType(receivedInto, ASSET);

        return Transaction.record(date, description, List.of(
                Entry.of(receivedInto, amount),
                Entry.of(lender, amount.negated())));
    }

    /** HU16. You pay down what you owe. */
    public static Transaction repayDebt(
            LocalDate date, String description,
            Account paidFrom, Account lender, Money amount) {

        requirePositive("A debt payment", amount);
        requireType(paidFrom, ASSET);
        requireType(lender, PAYABLE);

        return Transaction.record(date, description, List.of(
                Entry.of(paidFrom, amount.negated()),
                Entry.of(lender, amount)));
    }

    /** HU17. Someone hands you money to keep. Not income. */
    public static Transaction receiveThirdPartyMoney(
            LocalDate date, String description,
            Account fund, Account heldIn, Money amount) {

        requirePositive("A third-party deposit", amount);
        requireType(fund, THIRD_PARTY_FUND);
        requireType(heldIn, ASSET);

        return Transaction.record(date, description, List.of(
                Entry.of(heldIn, amount),
                Entry.of(fund, amount.negated())));
    }

    /**
     * HU18. You spend their money on their behalf. Not your expense, so no
     * expense category is touched: the fund itself is the other side.
     */
    public static Transaction spendThirdPartyMoney(
            LocalDate date, String description,
            Account fund, Account paidFrom, Money amount) {

        return drawDownFund("A third-party payment", date, description, fund, paidFrom, amount);
    }

    /**
     * HU20. You hand their money back.
     *
     * <p>Structurally identical to {@link #spendThirdPartyMoney}: in both cases
     * your cash goes down and their fund goes down. Two names because the
     * history has to say which one happened; one implementation because the
     * ledger genuinely does not care.
     */
    public static Transaction returnThirdPartyMoney(
            LocalDate date, String description,
            Account fund, Account paidFrom, Money amount) {

        return drawDownFund("A third-party refund", date, description, fund, paidFrom, amount);
    }

    private static Transaction drawDownFund(
            String operation, LocalDate date, String description,
            Account fund, Account paidFrom, Money amount) {

        requirePositive(operation, amount);
        requireType(fund, THIRD_PARTY_FUND);
        requireType(paidFrom, ASSET);

        return Transaction.record(date, description, List.of(
                Entry.of(paidFrom, amount.negated()),
                Entry.of(fund, amount)));
    }

    private static void requirePositive(String operation, Money amount) {
        if (!amount.isPositive()) {
            throw new NonPositiveAmountException(operation, amount);
        }
    }

    private static void requireType(Account account, AccountType... allowed) {
        for (AccountType candidate : allowed) {
            if (account.type() == candidate) {
                return;
            }
        }
        throw new UnexpectedAccountTypeException(account, allowed);
    }

    private Operations() {
    }
}
