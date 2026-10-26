package com.personalfinance.application.read;

import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * HU22. Los indicadores del libro a una fecha.
 *
 * <p>{@code moneyReallyMine} es lo que queda al sacar la plata que solo pasa por
 * sus manos: el banco dice 800.000, pero 100.000 son de su mamá y 200.000 son
 * de un préstamo del hermano. Los totales por tipo van con el signo que lee la
 * persona, ya aplicado por el dominio, y de ellos salen cuatro indicadores más
 * de HU22: dinero disponible, lo que le deben, lo que debe y lo de terceros.
 *
 * <p>HU22 pide «Ingresos», «Gastos» <strong>y</strong> «Gastos del mes» como
 * indicadores distintos, así que son cosas distintas: los totales por tipo son
 * el acumulado de toda la historia del libro, y estos dos son solo el mes de
 * {@code asOf}. Un acumulado histórico de gastos no responde «cómo voy este
 * mes», que es la única pregunta que se hace frente a un tablero.
 *
 * <p>Es también el punto de una serie. {@code GET /api/summary} devuelve uno y
 * la evolución en el tiempo devuelve una lista de estos, uno a fin de cada mes:
 * un solo record para las dos lecturas, en vez de dos formas que un cliente
 * tendría que aprender por separado y alguien tendría que arreglar dos veces el
 * día que se agregue un campo.
 *
 * @param asOf             la fecha a la que se respondió; sin ella el número no
 *                         significa nada dos días después
 * @param monthFrom        el día 1 del mes de {@code asOf}, que es desde donde
 *                         se contaron los dos del mes; hasta dónde es
 *                         {@code asOf}
 * @param incomeThatMonth  lo que entró entre {@code monthFrom} y {@code asOf}
 */
public record BookSummary(
        LocalDate asOf,
        Money moneyReallyMine,
        Map<AccountType, Money> totalsByType,
        LocalDate monthFrom,
        Money incomeThatMonth,
        Money expensesThatMonth) {

    public BookSummary {
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(moneyReallyMine, "moneyReallyMine");
        Objects.requireNonNull(monthFrom, "monthFrom");
        Objects.requireNonNull(incomeThatMonth, "incomeThatMonth");
        Objects.requireNonNull(expensesThatMonth, "expensesThatMonth");
        totalsByType = Map.copyOf(Objects.requireNonNull(totalsByType, "totalsByType"));
    }

    public Money totalFor(AccountType type) {
        return totalsByType.getOrDefault(type, Money.ZERO);
    }
}
