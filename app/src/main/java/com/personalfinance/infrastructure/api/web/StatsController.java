package com.personalfinance.infrastructure.api.web;

import com.personalfinance.application.read.MovementFilter;
import com.personalfinance.application.read.ReadBook;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.domain.ledger.Entry;
import com.personalfinance.domain.ledger.PersonId;
import com.personalfinance.domain.shared.Money;
import com.personalfinance.infrastructure.api.dto.response.OverTimeResponse;
import com.personalfinance.infrastructure.api.dto.response.TotalsByAccountResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU23. Las estadísticas: las series que se dibujan.
 *
 * <p>Dos endpoints para las cinco que pide la historia, y no cinco. La serie de
 * indicadores trae la evolución del saldo, el dinero prestado, el adeudado y
 * los ingresos contra los gastos de cada mes, porque las cuatro son el mismo
 * recorrido de los mismos meses mirando campos distintos; el desglose por
 * cuenta trae los gastos por categoría. Cinco endpoints habrían devuelto tres
 * veces la misma serie recortada de tres formas.
 *
 * <p>La agregación se hace en el dominio y no en el cliente. Sumar plata es
 * aritmética contable, y repetirla en Dart rompería «una regla, un lugar» en el
 * peor sitio posible: el teléfono mostraría un total y el servidor otro, y los
 * dos parecerían correctos.
 *
 * <p>Un controlador no hace nada más que validar, traducir, llamar y traducir
 * de vuelta. El rango y el tope de meses son validación del borde; los totales,
 * del libro.
 */
@RestController
@RequestMapping(path = "/api/stats", produces = MediaType.APPLICATION_JSON_VALUE)
class StatsController {

    /** El mismo tope que en el historial, por la misma razón. */
    private static final int MAX_ACCOUNTS_PER_QUERY = 10;

    private final ReadBook book;
    private final Clock clock;

    StatsController(ReadBook book, Clock clock) {
        this.book = book;
        this.clock = clock;
    }

    /**
     * HU23. La serie de indicadores, un punto a fin de cada mes del rango.
     *
     * <p>No toma filtros, y eso es parte del diseño. Cada punto es un saldo, es
     * decir el acumulado desde el origen del libro; filtrarlo daría un número
     * con la misma pinta que ya no sería un saldo. El rango elige qué puntos se
     * dibujan y nada más.
     */
    @GetMapping("/over-time")
    OverTimeResponse overTime(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDate last = Dates.orToday(to, clock);
        LocalDate first = Dates.orAYearBefore(from, last);
        Dates.requireWithinTheSpan(first, last);

        return OverTimeResponse.of(first, last, book.overTime(first, last));
    }

    /**
     * HU23. Cuánto movió cada cuenta en el periodo, la más grande primero.
     *
     * <p>Con {@code type=EXPENSE} esto es «gastos por categoría», que es el
     * gráfico que se mira primero. Aquí el tipo dice qué cuentas se suman, no
     * qué movimientos cuentan: como filtro de movimientos sería redundante —
     * un movimiento que no toca ninguna cuenta de ese tipo no aporta nada al
     * desglose — y como selector de apuntes es lo que evita meter en el mismo
     * gráfico los dos lados del mismo almuerzo.
     *
     * <p>Los demás filtros son los de HU25 y se aplican tal cual: «lo que gasté
     * por categoría, pero solo lo que salió de Nequi» es una pregunta legítima.
     */
    @GetMapping("/by-account")
    TotalsByAccountResponse byAccount(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AccountType type,
            @RequestParam(required = false) @Size(max = MAX_ACCOUNTS_PER_QUERY) List<UUID> account,
            @RequestParam(required = false) UUID person,
            @RequestParam(required = false) @Min(0) @Max(Entry.MAX_ABSOLUTE_CENTS) Long minInCents,
            @RequestParam(required = false) @Min(0) @Max(Entry.MAX_ABSOLUTE_CENTS) Long maxInCents) {

        LocalDate last = Dates.orToday(to, clock);
        LocalDate first = Dates.orAYearBefore(from, last);

        MovementFilter filter = new MovementFilter(
                first,
                last,
                null,
                account == null ? List.of() : account.stream().map(AccountId::new).toList(),
                person == null ? null : new PersonId(person),
                minInCents == null ? null : Money.ofCents(minInCents),
                maxInCents == null ? null : Money.ofCents(maxInCents));

        return TotalsByAccountResponse.of(first, last, book.totalsByAccount(type, filter));
    }
}
