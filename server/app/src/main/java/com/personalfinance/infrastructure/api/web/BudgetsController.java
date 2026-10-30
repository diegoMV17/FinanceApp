package com.personalfinance.infrastructure.api.web;

import com.personalfinance.application.read.ReadBook;
import com.personalfinance.application.usecase.SetBudget;
import com.personalfinance.infrastructure.api.dto.request.SetBudgetRequest;
import com.personalfinance.infrastructure.api.dto.response.BudgetResponse;
import com.personalfinance.infrastructure.api.dto.response.BudgetsResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU26 y HU27. Los presupuestos: ponerlos y ver cómo va el mes.
 *
 * <p>Un {@code POST} y no un {@code PUT} sobre la categoría, por lo mismo que
 * los renombrados y los archivados son sub-recursos en plural: lo que se manda
 * es el hecho «este es el límite», no un campo que se sobreescribe. Cambiar de
 * idea es mandar el hecho otra vez, y el 201 dice algo cierto las dos veces
 * porque las dos veces se escribió una fila nueva.
 *
 * <p>La ruta es {@code /api/budgets} y no {@code /api/accounts/{id}/budgets}
 * aunque un presupuesto sea de una categoría. La razón es la lectura: lo que se
 * pregunta es «cómo voy este mes», que es una pregunta sobre el mes y no sobre
 * una categoría, y tener la escritura y la lectura en el mismo recurso evita
 * que un cliente aprenda dos sitios para lo mismo.
 */
@RestController
@RequestMapping(path = "/api/budgets", produces = MediaType.APPLICATION_JSON_VALUE)
class BudgetsController {

    private final SetBudget setBudget;
    private final ReadBook book;
    private final Clock clock;
    private final ZoneId timezone;

    BudgetsController(SetBudget setBudget, ReadBook book, Clock clock, ZoneId timezone) {
        this.setBudget = setBudget;
        this.book = book;
        this.clock = clock;
        this.timezone = timezone;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<BudgetResponse> set(@Valid @RequestBody SetBudgetRequest request) {
        var budget = setBudget.execute(request.toCommand());
        return Answers.of(budget, BudgetResponse.of(budget.value()));
    }

    /**
     * HU27. Cómo va cada límite del mes, el más consumido primero.
     *
     * <p>Sin mes contesta el de hoy, y «hoy» es el día del calendario del
     * dueño: en UTC, a las siete de la tarde de un 31 ya sería el mes
     * siguiente, con todos los límites en cero justo la noche en que se mira el
     * cierre.
     *
     * <p>Esa misma fecha baja a la lectura, porque es hasta donde se cuenta lo
     * gastado del mes en curso: un gasto fechado la semana que viene todavía no
     * ocurrió y no se come el presupuesto de nadie. Va una sola vez y se usa
     * para las dos cosas, y se manda siempre, también cuando el mes vino
     * pedido: la fecha que se pidió es un mes, no un día.
     */
    @GetMapping
    BudgetsResponse budgets(
            @RequestParam(required = false)
            @Pattern(regexp = SetBudgetRequest.MONTH) String month) {

        LocalDate today = Dates.today(clock, timezone);
        YearMonth asked = month == null || month.isBlank()
                ? YearMonth.from(today)
                : YearMonth.parse(month);

        return BudgetsResponse.of(asked.toString(), book.budgets(asked, today));
    }
}
