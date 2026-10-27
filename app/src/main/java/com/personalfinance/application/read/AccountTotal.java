package com.personalfinance.application.read;

import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * HU23. Cuánto movió una cuenta en un periodo.
 *
 * <p>Con {@code type=EXPENSE} esto es «gastos por categoría», que es el gráfico
 * que la gente mira primero: una categoría <em>es</em> una cuenta de gasto, así
 * que no hace falta un concepto nuevo para tenerlo.
 *
 * <p>Se parece a {@link AccountBalance} y no es lo mismo. Un saldo es acumulado
 * desde el origen del tiempo; esto es lo que se movió entre dos fechas. Llamar
 * «saldo» a los 350.000 de Alimentación en marzo sería decir que Alimentación
 * tiene 350.000 guardados, que no es una frase con sentido.
 *
 * <p>Las archivadas también salen, con su fecha, por lo mismo que en los
 * saldos: un informe de marzo tiene que seguir nombrando la categoría que se
 * dejó de usar en abril.
 */
public record AccountTotal(
        AccountId id, AccountType type, String name, Money total, Instant archivedAt) {

    public AccountTotal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(total, "total");
    }

    static AccountTotal of(Account account, Money total) {
        return new AccountTotal(
                account.id(),
                account.type(),
                account.name(),
                total,
                account.archivedAt().orElse(null));
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public Optional<Instant> archivedOn() {
        return Optional.ofNullable(archivedAt);
    }
}
