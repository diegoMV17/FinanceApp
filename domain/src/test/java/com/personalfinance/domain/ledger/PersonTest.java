package com.personalfinance.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Block J, first half: a person, on their own.
 *
 * <p>A person is the answer to "how do Juan and I stand?", which today needs
 * three accounts read at once: what he owes me, what I owe him and the money of
 * his I am holding. This class is only the identity; the arithmetic is
 * {@link PersonPosition} and the catalogue is {@link Directory}.
 */
@DisplayName("A person")
class PersonTest {

    private static final Instant NOW = Instant.parse("2026-10-22T20:00:00Z");

    @Test
    @DisplayName("J1 · opens under the id the caller brought, or mints one")
    void opens() {
        PersonId id = PersonId.generate();

        assertThat(Person.open(id, "Juan").id()).isEqualTo(id);
        assertThat(Person.open("Juan").id()).isNotNull();
    }

    @Test
    @DisplayName("J2 · trims its name, and a name of only blanks is no name")
    void whatANameMayNotBe() {
        assertThat(Person.open("  Juan  ").name()).isEqualTo("Juan");

        assertThatExceptionOfType(BlankPersonNameException.class)
                .isThrownBy(() -> Person.open("   "));
    }

    /**
     * The same distinction {@link Account} draws, and for the same reason:
     * mixing the two loses a rename. "juan" and "Juan" are the same name when
     * deciding whether two people would be indistinguishable in a selector, and
     * different names when deciding whether a rename has anything to do.
     */
    @Test
    @DisplayName("J3 · answers to its name ignoring capitals, and is named only exactly")
    void answersToAndIsNamed() {
        Person juan = Person.open("Juan");

        assertThat(juan.answersTo("juan")).isTrue();
        assertThat(juan.answersTo("  JUAN  ")).isTrue();
        assertThat(juan.answersTo("Juana")).isFalse();

        assertThat(juan.isNamed("Juan")).isTrue();
        assertThat(juan.isNamed("  Juan  ")).isTrue();
        assertThat(juan.isNamed("juan")).isFalse();
    }

    @Test
    @DisplayName("J4 · archives once, and not twice")
    void archivesOnce() {
        Person juan = Person.open("Juan");

        juan.archive(NOW);

        assertThat(juan.isArchived()).isTrue();
        assertThat(juan.archivedAt()).contains(NOW);

        assertThatExceptionOfType(PersonAlreadyArchivedException.class)
                .isThrownBy(() -> juan.archive(NOW.plusSeconds(60)));

        assertThat(juan.archivedAt()).contains(NOW);
    }

    @Test
    @DisplayName("J5 · is open until it is archived")
    void openUntilArchived() {
        Person juan = Person.open("Juan");

        assertThat(juan.isArchived()).isFalse();
        assertThat(juan.archivedAt()).isEmpty();
    }

    @Test
    @DisplayName("J6 · is the same person by id, whatever the name says")
    void identityIsTheId() {
        PersonId id = PersonId.generate();

        Person one = Person.open(id, "Juan");
        Person other = Person.open(id, "Juan Pérez");

        assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
        assertThat(one).isNotEqualTo(Person.open("Juan"));
    }

    @Test
    @DisplayName("J7 · says whether it is archived when printed")
    void printsItsState() {
        Person juan = Person.open("Juan");
        assertThat(juan).hasToString("Juan");

        juan.archive(NOW);
        assertThat(juan.toString()).contains("Juan", "archivada");
    }
}
