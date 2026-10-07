package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import java.util.List;

/**
 * A fresh set of accounts plus a way to turn a generated {@link Step} into a
 * real transaction.
 *
 * <p>Property tests need to build hundreds of ledgers from random instructions.
 * This is the one place that knows how to do that, so the properties themselves
 * stay readable: they state what must always be true, not how to set it up.
 */
final class LedgerScenario {

    static final LocalDate START = LocalDate.of(2026, 1, 1);

    /** The templates a generated history can draw from. */
    enum OperationKind {
        OPENING_BALANCE,
        INCOME,
        EXPENSE,
        TRANSFER,
        LEND,
        COLLECT_LOAN,
        BORROW,
        REPAY_DEBT,
        RECEIVE_FUND,
        SPEND_FUND,
        RETURN_FUND;

        static final List<OperationKind> THIRD_PARTY_ONLY =
                List.of(RECEIVE_FUND, SPEND_FUND, RETURN_FUND);
    }

    /** One generated instruction. Amounts are always positive, as a person states them. */
    record Step(OperationKind kind, long amount, int dayOffset) {
    }

    final Account bancolombia = Account.open(AccountType.ASSET, "Bancolombia");
    final Account nequi = Account.open(AccountType.ASSET, "Nequi");
    final Account cash = Account.open(AccountType.ASSET, "Efectivo");
    final Account food = Account.open(AccountType.EXPENSE, "Alimentación");
    final Account salary = Account.open(AccountType.INCOME, "Salario");
    final Account juan = Account.open(AccountType.RECEIVABLE, "Juan");
    final Account brother = Account.open(AccountType.PAYABLE, "Hermano");
    final Account momsFund = Account.open(AccountType.THIRD_PARTY_FUND, "Mamá");
    final Account openingBalances = Account.open(AccountType.EQUITY, "Saldos iniciales");

    final List<Account> allAccounts = List.of(
            bancolombia, nequi, cash, food, salary, juan, brother, momsFund, openingBalances);

    Transaction transactionFor(Step step) {
        LocalDate on = START.plusDays(step.dayOffset());
        Money amount = Money.ofUnits(step.amount());
        String what = step.kind().name();

        return switch (step.kind()) {
            case OPENING_BALANCE ->
                    Operations.openingBalance(on, bancolombia, openingBalances, amount);
            case INCOME -> Operations.income(on, what, bancolombia, salary, amount);
            case EXPENSE -> Operations.expense(on, what, cash, food, amount);
            case TRANSFER -> Operations.transfer(on, what, bancolombia, nequi, amount);
            case LEND -> Operations.lend(on, what, bancolombia, juan, amount);
            case COLLECT_LOAN -> Operations.collectLoan(on, what, juan, bancolombia, amount);
            case BORROW -> Operations.borrow(on, what, brother, cash, amount);
            case REPAY_DEBT -> Operations.repayDebt(on, what, cash, brother, amount);
            case RECEIVE_FUND ->
                    Operations.receiveThirdPartyMoney(on, what, momsFund, cash, amount);
            case SPEND_FUND -> Operations.spendThirdPartyMoney(on, what, momsFund, cash, amount);
            case RETURN_FUND -> Operations.returnThirdPartyMoney(on, what, momsFund, cash, amount);
        };
    }

    Ledger ledgerFrom(List<Step> steps) {
        Ledger ledger = new Ledger();
        steps.forEach(step -> ledger.record(transactionFor(step)));
        return ledger;
    }

    /** A ledger holding only the steps that happened on or before {@code asOf}. */
    Ledger ledgerTruncatedAt(List<Step> steps, LocalDate asOf) {
        Ledger ledger = new Ledger();
        steps.stream()
                .map(this::transactionFor)
                .filter(transaction -> !transaction.date().isAfter(asOf))
                .forEach(ledger::record);
        return ledger;
    }

    LocalDate dayAfterStart(int days) {
        return START.plusDays(days);
    }
}
