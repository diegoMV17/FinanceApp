package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.BookSummary;
import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * El resumen del libro a una fecha.
 *
 * <p>{@code moneyReallyMineInCents} es la pregunta que da sentido a la
 * aplicación: cuánto de lo que hay en las cuentas es de verdad suyo, descontando
 * lo que debe y lo que solo está guardando para otro.
 *
 * <p>Los totales se devuelven con las siete claves de {@link AccountType}
 * siempre, incluso en cero. {@code ReadBook} ya los arma completos, y mantenerlo
 * así significa que un cliente nunca tiene que distinguir «este tipo no vino»
 * de «este tipo está en cero»: un gráfico se dibuja igual los dos primeros días
 * de uso y el año que viene.
 *
 * <p>Se usa un {@link EnumMap} para que el JSON salga en el orden en que están
 * declarados los tipos, que es de lo más útil a lo más raro, y no en el orden en
 * que un {@code HashMap} decida.
 *
 * <p>Los dos del mes van aparte de los totales por tipo porque son otra cosa:
 * HU22 pide «Ingresos», «Gastos» y «Gastos del mes» como tres indicadores. Los
 * totales por tipo son el acumulado de toda la historia; estos dos van del
 * {@code monthFrom} — el día 1 del mes de {@code asOf} — hasta {@code asOf}, que
 * es lo que significa «del mes» en una lectura «a una fecha». El {@code monthFrom}
 * viaja en la respuesta por lo mismo que el {@code asOf}: lo eligió el servidor.
 */
public record SummaryResponse(
        LocalDate asOf,
        long moneyReallyMineInCents,
        Map<AccountType, Long> totalsByTypeInCents,
        LocalDate monthFrom,
        long incomeThatMonthInCents,
        long expensesThatMonthInCents) {

    public static SummaryResponse of(BookSummary summary) {
        Map<AccountType, Long> totals = new EnumMap<>(AccountType.class);
        for (AccountType type : AccountType.values()) {
            totals.put(type, summary.totalFor(type).cents());
        }
        return new SummaryResponse(
                summary.asOf(),
                summary.moneyReallyMine().cents(),
                totals,
                summary.monthFrom(),
                summary.incomeThatMonth().cents(),
                summary.expensesThatMonth().cents());
    }

    /**
     * Copia defensiva que conserva el orden.
     *
     * <p>{@code Map.copyOf} serviría para que nadie toque el mapa de dentro,
     * pero devuelve un mapa cuyo orden de recorrido no está especificado, y con
     * eso se perdería justo lo que se buscó con el {@link EnumMap}: que el JSON
     * salga en el orden en que los tipos están declarados.
     */
    public SummaryResponse {
        totalsByTypeInCents = Collections.unmodifiableMap(
                new EnumMap<>(totalsByTypeInCents));
    }

    /** Lo que el libro dice que vale un tipo de cuenta, en centavos. */
    public long totalFor(AccountType type) {
        return totalsByTypeInCents.getOrDefault(type, Money.ZERO.cents());
    }
}
