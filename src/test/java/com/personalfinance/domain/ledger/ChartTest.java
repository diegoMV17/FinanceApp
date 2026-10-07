package com.personalfinance.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Block E, second half: the catalogue of accounts itself (HU08, HU11). */
@DisplayName("The chart of accounts")
class ChartTest {

    private static final Instant NOW = Instant.parse("2026-10-06T20:00:00Z");

    private final Chart chart = new Chart();
    private final Ledger ledger = new Ledger();

    @Test
    @DisplayName("E8 · hands out one instance per account")
    void oneInstancePerAccount() {
        Account opened = chart.open(AccountType.ASSET, "Bancolombia");

        assertThat(chart.require(opened.id())).isSameAs(opened);
        assertThat(chart.find(opened.id())).contains(opened);
    }

    @Test
    @DisplayName("E9 · refuses two open accounts of one kind with the same name")
    void refusesDuplicateNames() {
        chart.open(AccountType.ASSET, "Nequi");

        assertThatExceptionOfType(DuplicateAccountNameException.class)
                .isThrownBy(() -> chart.open(AccountType.ASSET, "Nequi"));

        assertThatExceptionOfType(DuplicateAccountNameException.class)
                .isThrownBy(() -> chart.open(AccountType.ASSET, "  nequi  "));
    }

    @Test
    @DisplayName("E9b · allows the same name under a different kind")
    void allowsTheSameNameAcrossKinds() {
        chart.open(AccountType.ASSET, "Nequi");

        Account category = chart.open(AccountType.EXPENSE, "Nequi");

        assertThat(category.type()).isEqualTo(AccountType.EXPENSE);
    }

    @Test
    @DisplayName("E10 · renames, and refuses a rename onto a name in use")
    void renaming() {
        Account bank = chart.open(AccountType.ASSET, "Bancolombia");
        chart.open(AccountType.ASSET, "Nequi");

        chart.rename(bank.id(), "Bancolombia Ahorros");
        assertThat(bank.name()).isEqualTo("Bancolombia Ahorros");

        assertThatExceptionOfType(DuplicateAccountNameException.class)
                .isThrownBy(() -> chart.rename(bank.id(), "Nequi"));
    }

    @Test
    @DisplayName("E10b · lets an account keep its own name, trimming aside")
    void renamingToItself() {
        Account bank = chart.open(AccountType.ASSET, "Bancolombia");

        chart.rename(bank.id(), "  Bancolombia  ");

        assertThat(bank.name()).isEqualTo("Bancolombia");
    }

    @Test
    @DisplayName("E11 · refuses a blank name")
    void refusesBlankNames() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> chart.open(AccountType.ASSET, "   "));
    }

    @Test
    @DisplayName("E12 · complains about an account it never held")
    void unknownAccount() {
        assertThatExceptionOfType(UnknownAccountException.class)
                .isThrownBy(() -> chart.require(AccountId.generate()));

        assertThat(chart.find(AccountId.generate())).isEmpty();
    }

    @Test
    @DisplayName("E13 · takes in an account built elsewhere, archived ones included")
    void adoptsExistingAccounts() {
        Account loaded = Account.open(AccountType.ASSET, "Daviplata");

        chart.add(loaded);

        assertThat(chart.selectableOf(AccountType.ASSET)).containsExactly(loaded);

        chart.archive(loaded.id(), ledger, NOW);
        Account reopened = chart.open(AccountType.ASSET, "Daviplata");

        assertThat(chart.selectableOf(AccountType.ASSET)).containsExactly(reopened);
        assertThat(chart.all()).contains(loaded, reopened);
    }
}
