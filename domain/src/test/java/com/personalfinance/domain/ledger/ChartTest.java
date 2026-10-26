package com.personalfinance.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
        assertThatExceptionOfType(BlankAccountNameException.class)
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
    @DisplayName("E14 · opens an account under the id the caller brought")
    void opensWithAGivenId() {
        AccountId id = AccountId.generate();

        Account opened = chart.open(id, AccountType.ASSET, "Bancolombia");

        assertThat(opened.id()).isEqualTo(id);
        assertThat(chart.require(id)).isSameAs(opened);
    }

    /**
     * Replacing the instance silently is what splits a balance in two: the
     * entries already written point at the old object, the new ones at the new.
     */
    @Test
    @DisplayName("E15 · refuses an id it already holds instead of replacing it")
    void refusesADuplicateId() {
        Account bank = chart.open(AccountType.ASSET, "Bancolombia");

        assertThatExceptionOfType(DuplicateAccountException.class)
                .isThrownBy(() -> chart.open(bank.id(), AccountType.EXPENSE, "Otra cosa"));

        assertThatExceptionOfType(DuplicateAccountException.class)
                .isThrownBy(() -> chart.add(Account.rehydrated(
                        bank.id(), AccountType.EXPENSE, "Otra cosa", null, null)));

        assertThat(chart.require(bank.id())).isSameAs(bank);
        assertThat(chart.all()).containsExactly(bank);
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

    @Test
    @DisplayName("E16 · links an account to a person, and moves the link to another")
    void linksToAPerson() {
        Person juan = new Directory().open("Juan");
        Person pedro = new Directory().open("Pedro");
        Account owed = chart.open(AccountType.RECEIVABLE, "El del taller");

        chart.linkToPerson(owed.id(), juan);
        assertThat(owed.person()).contains(juan);
        assertThat(chart.allOf(juan)).containsExactly(owed);

        chart.linkToPerson(owed.id(), pedro);
        assertThat(owed.person()).contains(pedro);
        assertThat(chart.allOf(juan)).isEmpty();
        assertThat(chart.allOf(pedro)).containsExactly(owed);
    }

    /**
     * Only the three kinds that are somebody. A category with a person attached
     * is nonsense that would then show up inside that person's position, and the
     * CHECK in the V1 refuses it outside the application too.
     */
    @Test
    @DisplayName("E17 · refuses to link a kind of account that is nobody")
    void refusesKindsThatAreNobody() {
        Person juan = new Directory().open("Juan");

        for (AccountType type : AccountType.values()) {
            if (type.belongsToAPerson()) {
                continue;
            }
            Account account = chart.open(type, "Cuenta " + type);

            assertThatExceptionOfType(AccountCannotBelongToAPersonException.class)
                    .isThrownBy(() -> chart.linkToPerson(account.id(), juan));

            assertThat(account.person()).isEmpty();
        }
    }

    @Test
    @DisplayName("E18 · and refuses an archived account or an archived person")
    void refusesArchivedEnds() {
        Directory directory = new Directory();
        Person juan = directory.open("Juan");
        Person retired = directory.open("Retirado");
        directory.archive(retired.id(), chart, NOW);

        Account open = chart.open(AccountType.PAYABLE, "Hermano");
        Account archived = chart.open(AccountType.PAYABLE, "Vecino");
        chart.archive(archived.id(), ledger, NOW);

        assertThatExceptionOfType(ArchivedAccountException.class)
                .isThrownBy(() -> chart.linkToPerson(archived.id(), juan));

        assertThatExceptionOfType(ArchivedPersonException.class)
                .isThrownBy(() -> chart.linkToPerson(open.id(), retired));

        assertThat(open.person()).isEmpty();
        assertThat(archived.person()).isEmpty();
    }

    @Test
    @DisplayName("E19 · lists what belongs to a person, archived accounts included")
    void listsWhatBelongsToAPerson() {
        Person juan = new Directory().open("Juan");
        Account owed = chart.open(AccountType.RECEIVABLE, "Juan");
        Account owing = chart.open(AccountType.PAYABLE, "Juan");
        chart.linkToPerson(owed.id(), juan);
        chart.linkToPerson(owing.id(), juan);
        chart.open(AccountType.ASSET, "Bancolombia");

        chart.archive(owing.id(), ledger, NOW);

        assertThat(chart.allOf(juan)).containsExactly(owed, owing);
        assertThat(chart.selectableOf(juan)).containsExactly(owed);
    }
}
