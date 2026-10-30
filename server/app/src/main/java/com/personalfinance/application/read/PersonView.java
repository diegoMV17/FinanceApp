package com.personalfinance.application.read;

import com.personalfinance.domain.ledger.Chart;
import com.personalfinance.domain.ledger.Ledger;
import com.personalfinance.domain.ledger.Person;
import com.personalfinance.domain.ledger.PersonId;
import com.personalfinance.domain.ledger.PersonPosition;
import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * HU19, HU21. Cómo está la cuenta con una persona, y con qué cuentas.
 *
 * <p>Los tres lados los calcula el dominio ({@link PersonPosition}); esta capa
 * solo les agrega el detalle de las cuentas, reusando el {@link AccountBalance}
 * que ya existe. Los números vienen con el signo que lee la persona: lo que
 * Juan me debe, lo que le debo y cuánto de su plata tengo, los tres en
 * positivo.
 *
 * <p>Es el mismo record para la lista y para una sola persona. Dos formas para
 * lo mismo obligarían a un cliente a aprender dos, y serían dos sitios donde
 * arreglar el día que se agregue un campo.
 *
 * @param archivedAt cuándo se archivó, o {@code null} si sigue abierta
 */
public record PersonView(
        PersonId id,
        String name,
        Instant archivedAt,
        Money owedToYou,
        Money youOwe,
        Money theirFund,
        Money netOwedToYou,
        List<AccountBalance> accounts) {

    public PersonView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(owedToYou, "owedToYou");
        Objects.requireNonNull(youOwe, "youOwe");
        Objects.requireNonNull(theirFund, "theirFund");
        Objects.requireNonNull(netOwedToYou, "netOwedToYou");
        accounts = List.copyOf(Objects.requireNonNull(accounts, "accounts"));
    }

    static PersonView of(Person person, Chart chart, Ledger ledger, java.time.LocalDate asOf) {
        PersonPosition position = PersonPosition.of(person, chart, ledger, asOf);

        List<AccountBalance> theirs = chart.allOf(person).stream()
                .map(account -> AccountBalance.of(
                        account, ledger.displayedBalanceOf(account, asOf)))
                .toList();

        return new PersonView(
                position.id(),
                position.name(),
                position.archivedAt(),
                position.owedToYou(),
                position.youOwe(),
                position.theirFund(),
                position.netOwedToYou(),
                theirs);
    }

    public boolean isArchived() {
        return archivedAt != null;
    }
}
