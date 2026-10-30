package com.personalfinance.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Block J, second half: the catalogue of people (HU19).
 *
 * <p>Same shape as {@link Chart}, and for the same reasons: one instance per
 * person, nothing ever removed, and names unique among the ones you can still
 * pick. Archiving is the only thing that needs an outside opinion, so it is the
 * only method that takes one.
 */
@DisplayName("The directory of people")
class DirectoryTest {

    private static final Instant NOW = Instant.parse("2026-10-22T20:00:00Z");

    private final Directory directory = new Directory();
    private final Chart chart = new Chart();
    private final Ledger ledger = new Ledger();

    @Test
    @DisplayName("J9 · hands out one instance per person")
    void oneInstancePerPerson() {
        Person juan = directory.open("Juan");

        assertThat(directory.require(juan.id())).isSameAs(juan);
        assertThat(directory.find(juan.id())).contains(juan);
    }

    @Test
    @DisplayName("J10 · refuses two open people with the same name")
    void refusesDuplicateNames() {
        directory.open("Juan");

        assertThatExceptionOfType(DuplicatePersonNameException.class)
                .isThrownBy(() -> directory.open("Juan"));

        assertThatExceptionOfType(DuplicatePersonNameException.class)
                .isThrownBy(() -> directory.open("  juan  "));
    }

    @Test
    @DisplayName("J11 · and a name freed by archiving can be used again")
    void archivingFreesTheName() {
        Person juan = directory.open("Juan");
        directory.archive(juan.id(), chart, NOW);

        Person another = directory.open("Juan");

        assertThat(directory.selectable()).containsExactly(another);
        assertThat(directory.all()).containsExactly(juan, another);
    }

    @Test
    @DisplayName("J12 · renames, and refuses a rename onto a name in use")
    void renaming() {
        Person juan = directory.open("Juan");
        directory.open("Pedro");

        directory.rename(juan.id(), "Juan Pérez");
        assertThat(juan.name()).isEqualTo("Juan Pérez");

        assertThatExceptionOfType(DuplicatePersonNameException.class)
                .isThrownBy(() -> directory.rename(juan.id(), "Pedro"));
    }

    @Test
    @DisplayName("J13 · lets a person keep their own name, trimming aside")
    void renamingToItself() {
        Person juan = directory.open("Juan");

        directory.rename(juan.id(), "  Juan  ");

        assertThat(juan.name()).isEqualTo("Juan");
    }

    /**
     * Replacing the instance is what would split a person in two: the accounts
     * already linked point at the old object, and a later link at the new one.
     */
    @Test
    @DisplayName("J14 · refuses an id it already holds instead of replacing it")
    void refusesADuplicateId() {
        Person juan = directory.open("Juan");

        assertThatExceptionOfType(DuplicatePersonException.class)
                .isThrownBy(() -> directory.open(juan.id(), "Pedro"));

        assertThatExceptionOfType(DuplicatePersonException.class)
                .isThrownBy(() -> directory.add(Person.rehydrated(juan.id(), "Pedro", null)));

        assertThat(directory.require(juan.id())).isSameAs(juan);
        assertThat(directory.all()).containsExactly(juan);
    }

    @Test
    @DisplayName("J15 · complains about a person it never held")
    void unknownPerson() {
        assertThatExceptionOfType(UnknownPersonException.class)
                .isThrownBy(() -> directory.require(PersonId.generate()));

        assertThat(directory.find(PersonId.generate())).isEmpty();
    }

    @Test
    @DisplayName("J16 · archives a person once every account of theirs is archived")
    void archivesWhenNothingIsLeftOpen() {
        Person juan = directory.open("Juan");
        Account owedByJuan = chart.open(AccountType.RECEIVABLE, "Juan");
        chart.linkToPerson(owedByJuan.id(), juan);
        chart.archive(owedByJuan.id(), ledger, NOW);

        directory.archive(juan.id(), chart, NOW);

        assertThat(juan.isArchived()).isTrue();
        assertThat(directory.archived()).containsExactly(juan);
    }

    /**
     * One rule, one place. An account that holds money refuses to archive with a
     * balance, so "every account archived" already means Juan owes nothing and
     * is owed nothing. Re-deriving a balance here would be a second truth.
     */
    @Test
    @DisplayName("J17 · and refuses to archive one who still has an open account")
    void refusesToArchiveWithOpenAccounts() {
        Person juan = directory.open("Juan");
        Account owedByJuan = chart.open(AccountType.RECEIVABLE, "Juan");
        chart.linkToPerson(owedByJuan.id(), juan);

        assertThatExceptionOfType(PersonStillHasOpenAccountsException.class)
                .isThrownBy(() -> directory.archive(juan.id(), chart, NOW))
                .satisfies(thrown -> assertThat(thrown.openAccounts())
                        .containsExactly(owedByJuan));

        assertThat(juan.isArchived()).isFalse();
    }

    @Test
    @DisplayName("J18 · lists everyone, and separately who can still be picked")
    void listsThem() {
        Person juan = directory.open("Juan");
        Person pedro = directory.open("Pedro");
        directory.archive(pedro.id(), chart, NOW);

        assertThat(directory.all()).containsExactly(juan, pedro);
        assertThat(directory.selectable()).containsExactly(juan);
        assertThat(directory.archived()).containsExactly(pedro);
    }
}
