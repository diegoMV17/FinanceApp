package com.personalfinance.application.read;

import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * HU22. La respuesta a "cuánto tengo de verdad", a una fecha.
 *
 * <p>{@code moneyReallyMine} es lo que queda al sacar la plata que solo pasa por
 * sus manos: el banco dice 800.000, pero 100.000 son de su mamá y 200.000 son
 * de un préstamo del hermano. Los totales por tipo van con el signo que lee la
 * persona, ya aplicado por el dominio.
 *
 * @param asOf la fecha a la que se respondió; sin ella el número no significa
 *             nada dos días después
 */
public record BookSummary(
        LocalDate asOf, Money moneyReallyMine, Map<AccountType, Money> totalsByType) {

    public BookSummary {
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(moneyReallyMine, "moneyReallyMine");
        totalsByType = Map.copyOf(Objects.requireNonNull(totalsByType, "totalsByType"));
    }

    public Money totalFor(AccountType type) {
        return totalsByType.getOrDefault(type, Money.ZERO);
    }
}
