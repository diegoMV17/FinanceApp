package com.personalfinance.domain.ledger;

import static com.personalfinance.domain.shared.Money.ofUnits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The catalogue and the ledger travel together or not at all. */
@DisplayName("A book")
class BookTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 6);

    @Test
    @DisplayName("F12 · starts empty and ready to use")
    void startsEmpty() {
        Book book = Book.empty();

        Account cash = book.chart().open(AccountType.ASSET, "Efectivo");
        Account food = book.chart().open(AccountType.EXPENSE, "Alimentación");
        book.ledger().record(Operations.expense(TransactionId.generate(),
                TODAY, "Almuerzo", cash, food, ofUnits(35_000)));

        assertThat(book.ledger().displayedBalanceOf(food, TODAY)).isEqualTo(ofUnits(35_000));
    }

    @Test
    @DisplayName("F12b · and the directory of people comes with it")
    void bringsItsDirectory() {
        Book book = Book.empty();

        Person juan = book.directory().open("Juan");
        Account owed = book.chart().open(AccountType.RECEIVABLE, "Juan");
        book.chart().linkToPerson(owed.id(), juan);

        assertThat(book.chart().allOf(juan)).containsExactly(owed);
    }

    @Test
    @DisplayName("F13 · is never part of a book")
    void refusesPartOfItself() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Book(new Chart(), null, new Directory()));
        assertThatNullPointerException()
                .isThrownBy(() -> new Book(null, new Ledger(), new Directory()));
        assertThatNullPointerException()
                .isThrownBy(() -> new Book(new Chart(), new Ledger(), null));
    }
}
