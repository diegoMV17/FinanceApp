package com.personalfinance.domain.ledger;

import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

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

    final Chart chart = new Chart();
    final Directory directory = new Directory();

    final Account bancolombia = chart.open(AccountType.ASSET, "Bancolombia");
    final Account nequi = chart.open(AccountType.ASSET, "Nequi");
    final Account cash = chart.open(AccountType.ASSET, "Efectivo");
    final Account food = chart.open(AccountType.EXPENSE, "Alimentación");
    final Account salary = chart.open(AccountType.INCOME, "Salario");
    final Account juan = chart.open(AccountType.RECEIVABLE, "Juan");
    final Account brother = chart.open(AccountType.PAYABLE, "Hermano");
    final Account momsFund = chart.open(AccountType.THIRD_PARTY_FUND, "Mamá");
    final Account openingBalances = chart.open(AccountType.EQUITY, "Saldos iniciales");

    final List<Account> allAccounts = List.of(
            bancolombia, nequi, cash, food, salary, juan, brother, momsFund, openingBalances);

    /**
     * Puts a person behind every account that is somebody, and hands them back.
     *
     * <p>Linking <em>all three</em> is what makes the property exact: these are
     * the only RECEIVABLE, PAYABLE and THIRD_PARTY_FUND accounts in the
     * scenario, so nothing of anybody can sit outside the people view.
     */
    List<Person> everybodyLinked() {
        Person juanHimself = directory.open("Juan");
        Person theBrother = directory.open("Hermano");
        Person mother = directory.open("Mamá");

        chart.linkToPerson(juan.id(), juanHimself);
        chart.linkToPerson(brother.id(), theBrother);
        chart.linkToPerson(momsFund.id(), mother);

        return List.of(juanHimself, theBrother, mother);
    }

    Transaction transactionFor(Step step) {
        LocalDate on = START.plusDays(step.dayOffset());
        Money amount = Money.ofUnits(step.amount());
        String what = step.kind().name();

        return switch (step.kind()) {
            case OPENING_BALANCE ->
                    Operations.openingBalance(TransactionId.generate(),
                            on, bancolombia, openingBalances, amount);
            case INCOME -> Operations.income(TransactionId.generate(),
                    on, what, bancolombia, salary, amount);
            case EXPENSE -> Operations.expense(TransactionId.generate(),
                    on, what, cash, food, amount);
            case TRANSFER -> Operations.transfer(TransactionId.generate(),
                    on, what, bancolombia, nequi, amount);
            case LEND -> Operations.lend(TransactionId.generate(),
                    on, what, bancolombia, juan, amount);
            case COLLECT_LOAN -> Operations.collectLoan(TransactionId.generate(),
                    on, what, juan, bancolombia, amount);
            case BORROW -> Operations.borrow(TransactionId.generate(),
                    on, what, brother, cash, amount);
            case REPAY_DEBT -> Operations.repayDebt(TransactionId.generate(),
                    on, what, cash, brother, amount);
            case RECEIVE_FUND ->
                    Operations.receiveThirdPartyMoney(TransactionId.generate(),
                            on, what, momsFund, cash, amount);
            case SPEND_FUND -> Operations.spendThirdPartyMoney(TransactionId.generate(),
                    on, what, momsFund, cash, amount);
            case RETURN_FUND -> Operations.returnThirdPartyMoney(TransactionId.generate(),
                    on, what, momsFund, cash, amount);
        };
    }

    Ledger ledgerFrom(List<Step> steps) {
        Ledger ledger = new Ledger();
        steps.forEach(step -> ledger.record(transactionFor(step)));
        return ledger;
    }

    /** Records everything, then cancels the steps whose position matches. */
    Ledger ledgerFromWithCancellations(List<Step> steps, IntPredicate cancelled, Instant when) {
        Ledger ledger = new Ledger();
        List<TransactionId> toCancel = new ArrayList<>();

        for (int position = 0; position < steps.size(); position++) {
            Transaction transaction = transactionFor(steps.get(position));
            ledger.record(transaction);
            if (cancelled.test(position)) {
                toCancel.add(transaction.id());
            }
        }
        toCancel.forEach(id -> ledger.cancel(id, when, "Generado"));
        return ledger;
    }

    /** The steps that would be left if those same positions had never happened. */
    List<Step> stepsSurviving(List<Step> steps, IntPredicate cancelled) {
        List<Step> survivors = new ArrayList<>();
        for (int position = 0; position < steps.size(); position++) {
            if (!cancelled.test(position)) {
                survivors.add(steps.get(position));
            }
        }
        return List.copyOf(survivors);
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
