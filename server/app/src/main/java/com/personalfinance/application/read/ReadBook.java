package com.personalfinance.application.read;

import com.personalfinance.application.BookLoader;
import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.domain.ledger.Book;
import com.personalfinance.domain.ledger.Ledger;
import com.personalfinance.domain.ledger.MovementQuery;
import com.personalfinance.domain.ledger.Person;
import com.personalfinance.domain.ledger.PersonId;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Todo lo que se lee del libro.
 *
 * <p>Las lecturas no toman el candado: no escriben nada y hacerlas esperar
 * detrás de una escritura no protege nada. Lo que sí necesitan es
 * {@code REPEATABLE_READ}. {@link BookLoader} hace dos consultas — primero el
 * catálogo, después los movimientos — y en READ COMMITTED una escritura que se
 * cuele entre las dos deja un apunte apuntando a una cuenta que no se cargó: la
 * carga revienta con {@code UnknownAccountException} y la lectura falla sin que
 * nada esté mal.
 *
 * <p>Devuelve records de esta capa y nunca entidades de persistencia. Tampoco
 * devuelve objetos del dominio: un {@code Account} tiene mutadores que solo el
 * agregado debe poder llamar, y lo que sale de una lectura va camino a un JSON.
 */
@Service
public class ReadBook {

    /**
     * La más grande primero, que es el orden en que se lee un gráfico de
     * categorías. El nombre desempata para que dos cuentas con el mismo total
     * no salgan hoy en un orden y mañana en otro.
     */
    private static final Comparator<AccountTotal> BIGGEST_FIRST =
            Comparator.comparing(AccountTotal::total, Comparator.reverseOrder())
                    .thenComparing(AccountTotal::name);

    /**
     * El más consumido primero: arriba queda lo que está a punto de reventar,
     * que es lo que se busca al abrir esta pantalla. El nombre desempata para
     * que dos categorías igual de gastadas no salgan hoy en un orden y mañana
     * en otro.
     */
    private static final Comparator<BudgetView> MOST_USED_FIRST =
            Comparator.comparingLong(BudgetView::percentageUsed).reversed()
                    .thenComparing(BudgetView::categoryName);

    private final BookLoader loader;

    ReadBook(BookLoader loader) {
        this.loader = loader;
    }

    /** HU22. Los indicadores a esa fecha, incluidos los dos del mes. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BookSummary summary(LocalDate asOf) {
        return summaryAt(loader.load().ledger(), asOf);
    }

    /**
     * HU23. Los mismos indicadores a fin de cada mes del rango: la evolución
     * del saldo, el dinero prestado y el adeudado, y los ingresos contra los
     * gastos de cada mes.
     *
     * <p><strong>El rango elige los puntos que se dibujan, no lo que se
     * cuenta.</strong> Cada punto es el acumulado desde el origen del libro,
     * porque un saldo no es otra cosa; si el {@code from} filtrara, el número
     * de marzo dejaría de ser un saldo y sería «lo que se movió en marzo», con
     * la misma pinta y otro significado. Mover el {@code from} no cambia
     * ninguno de los puntos que sigan estando en el rango, y hay un test que lo
     * exige.
     *
     * <p>El último punto es {@code to} y no el fin de su mes: un punto en el 31
     * de marzo cuando hoy es el 15 sería el saldo del futuro.
     *
     * @throws com.personalfinance.domain.ledger.ReversedRangeException
     *         si el rango viene con los extremos al revés
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<BookSummary> overTime(LocalDate from, LocalDate to) {
        // La misma regla de rangos que todo lo demás, en el mismo sitio: un
        // rango al revés se rechaza en vez de devolver una serie vacía.
        MovementQuery span = MovementQuery.everything()
                .between(Objects.requireNonNull(from, "from"), Objects.requireNonNull(to, "to"));

        Ledger ledger = loader.load().ledger();
        List<BookSummary> series = new ArrayList<>();
        YearMonth last = YearMonth.from(span.to());

        for (YearMonth month = YearMonth.from(span.from());
                !month.isAfter(last);
                month = month.plusMonths(1)) {

            LocalDate endOfMonth = month.atEndOfMonth();
            series.add(summaryAt(ledger, endOfMonth.isAfter(span.to()) ? span.to() : endOfMonth));
        }

        return List.copyOf(series);
    }

    /**
     * HU23. Cuánto movió cada cuenta en el periodo, la más grande primero.
     *
     * <p>Con {@code type = EXPENSE} esto es «gastos por categoría». Ahí el tipo
     * dice qué <em>apuntes</em> se suman y no qué movimientos cuentan: un
     * almuerzo toca Nequi y Alimentación, y meter los dos apuntes en el mismo
     * desglose pondría la misma plata dos veces con signos opuestos.
     *
     * <p>Salen todas las cuentas del catálogo, también las que no se movieron,
     * en cero. Es la misma razón por la que el resumen trae los siete tipos
     * aunque estén en cero: un cliente no debería tener que distinguir «no
     * vino» de «está en cero».
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<AccountTotal> totalsByAccount(AccountType type, MovementFilter filter) {
        Book book = loader.load();
        Map<AccountId, Money> moved =
                book.ledger().totalsByAccount(type, queryFrom(filter, book));

        return book.chart().all().stream()
                .filter(account -> type == null || account.type() == type)
                .map(account -> AccountTotal.of(
                        account, moved.getOrDefault(account.id(), Money.ZERO)))
                .sorted(BIGGEST_FIRST)
                .toList();
    }

    /** El saldo de cada cuenta, archivadas incluidas, en el orden en que se abrieron. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<AccountBalance> balances(LocalDate asOf) {
        Book book = loader.load();

        return book.chart().all().stream()
                .map(account -> AccountBalance.of(
                        account, book.ledger().displayedBalanceOf(account, asOf)))
                .toList();
    }

    /**
     * HU19, HU21. Cómo está la cuenta con cada persona, a esa fecha.
     *
     * <p>Las archivadas también, con su fecha, por lo mismo que las cuentas: un
     * reporte de marzo tiene que seguir nombrando a quien dejó de deberle plata
     * en abril.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<PersonView> people(LocalDate asOf) {
        Book book = loader.load();

        return book.directory().all().stream()
                .map(person -> PersonView.of(person, book.chart(), book.ledger(), asOf))
                .toList();
    }

    /**
     * HU19. La misma respuesta, para una sola persona.
     *
     * @throws com.personalfinance.domain.ledger.UnknownPersonException
     *         si el directorio no tiene esa persona
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PersonView person(PersonId id, LocalDate asOf) {
        Book book = loader.load();
        Person person = book.directory().require(id);

        return PersonView.of(person, book.chart(), book.ledger(), asOf);
    }

    /**
     * HU21, HU24. Los movimientos que siguen contando sobre una cuenta, el más
     * viejo primero y por páginas.
     *
     * <p>El orden es el que documenta el dominio y la página se corta sobre
     * él, de modo que recorrer las páginas en orden recorre la historia en
     * orden. Sin el tope, esta lectura devolvía el historial completo de la
     * cuenta: con un libro de años, una petición corta a cambio de una
     * respuesta sin límite.
     *
     * @throws com.personalfinance.domain.ledger.UnknownAccountException
     *         si el catálogo no tiene esa cuenta
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<MovementView> movementsOf(AccountId id, PageRequest asked) {
        Book book = loader.load();
        Account account = book.chart().require(id);

        List<MovementView> all = book.ledger().movementsOf(account).stream()
                .map(movement -> MovementView.of(movement, account))
                .toList();

        return Page.of(all, asked);
    }

    /**
     * HU24, HU25. El historial general, filtrado y por páginas.
     *
     * <p>Sin cuenta desde la que mirar, así que cada línea trae sus apuntes y
     * su valor en vez de un efecto. El filtro se resuelve contra el libro ya
     * cargado: una cuenta o una persona que no existe es un 404 y no una lista
     * vacía, que se leería como «no hubo movimientos».
     *
     * @throws com.personalfinance.domain.ledger.UnknownAccountException
     *         si se filtró por una cuenta que el catálogo no tiene
     * @throws com.personalfinance.domain.ledger.UnknownPersonException
     *         si se filtró por una persona que el directorio no tiene
     * @throws com.personalfinance.domain.ledger.ReversedRangeException
     *         si un rango viene con los extremos al revés
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<MovementLine> movements(MovementFilter filter, PageRequest asked) {
        Book book = loader.load();

        List<MovementLine> all =
                book.ledger().movementsMatching(queryFrom(filter, book)).stream()
                        .map(MovementLine::of)
                        .toList();

        return Page.of(all, asked);
    }

    /**
     * HU27. Cómo va cada límite de ese mes, el más consumido primero.
     *
     * <p>Lo gastado sale de los apuntes de ese mes, como todo número de aquí, y
     * se cruza con el límite en el dominio. Solo salen los límites vivos: los
     * que fueron reemplazados siguen en la base porque la historia los
     * necesita, pero no son el plan de nadie.
     *
     * <p>El orden es el más consumido primero, que es el que pone arriba lo que
     * está a punto de reventar: es la pregunta que se hace frente a esta
     * pantalla.
     *
     * @param today la fecha de hoy en el calendario del dueño, que es hasta
     *              dónde se cuenta el mes en curso
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<BudgetView> budgets(YearMonth month, LocalDate today) {
        Book book = loader.load();
        Map<AccountId, Money> spent = spentDuring(book, month, today);

        return book.budgets().liveFor(month).stream()
                .map(budget -> BudgetView.of(budget.progressWith(
                        spent.getOrDefault(budget.category().id(), Money.ZERO))))
                .sorted(MOST_USED_FIRST)
                .toList();
    }

    /** HU06. La cadena de versiones de un movimiento, la original primero. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<RevisionView> revisionsOf(TransactionId id) {
        return loader.load().ledger().revisionsOf(id).stream()
                .map(RevisionView::of)
                .toList();
    }

    /**
     * Los ids del borde, convertidos en la pregunta que el libro entiende.
     *
     * <p>El {@code require} de cada cuenta y de la persona es lo que hace que
     * un id inventado sea un 404. Devuelve el mismo id que entró, y eso es el
     * punto: lo que se busca es la queja, no el objeto.
     */
    /**
     * Los indicadores a una fecha, que es el punto de una serie y también la
     * respuesta de {@code GET /api/summary}: la lectura suelta es el último
     * punto de la evolución, y por eso las dos salen de aquí.
     */
    private static BookSummary summaryAt(Ledger ledger, LocalDate asOf) {
        Map<AccountType, Money> totals = new EnumMap<>(AccountType.class);
        for (AccountType type : AccountType.values()) {
            totals.put(type, ledger.totalFor(type, asOf));
        }

        LocalDate monthFrom = asOf.withDayOfMonth(1);
        MovementQuery thatMonth = MovementQuery.everything().between(monthFrom, asOf);

        return new BookSummary(
                asOf,
                ledger.moneyReallyMine(asOf),
                totals,
                monthFrom,
                ledger.totalFor(AccountType.INCOME, thatMonth),
                ledger.totalFor(AccountType.EXPENSE, thatMonth));
    }

    /**
     * Lo gastado en cada categoría durante ese mes, que en el mes en curso
     * llega hasta hoy y no hasta el día 31.
     *
     * <p>Es la misma ventana que usa el resumen — del día 1 hasta la fecha con
     * la que se contesta —, y tiene que serlo: los dos números se leen juntos
     * en la misma pantalla, y un gasto fechado un 25 hacía que el porcentaje
     * del límite dijera 80% mientras «gastos del mes» decía 30%. La regla del
     * libro es que un movimiento futuro no cuenta hasta su fecha, así que el
     * que estaba mal era el porcentaje.
     *
     * <p>Un mes que ya pasó se cuenta entero: acotarlo a hoy le quitaría los
     * gastos de sus últimos días. Y un mes que todavía no ha empezado no ha
     * gastado nada, que es lo que dice la misma regla llevada hasta el final;
     * sin este caso aparte el rango quedaría al revés y una pregunta
     * perfectamente razonable — cómo va el plan de diciembre — se contestaría
     * con una queja.
     */
    private static Map<AccountId, Money> spentDuring(
            Book book, YearMonth month, LocalDate today) {

        LocalDate firstDay = month.atDay(1);
        if (today.isBefore(firstDay)) {
            return Map.of();
        }

        LocalDate lastDay = month.atEndOfMonth();
        MovementQuery thatMonth = MovementQuery.everything()
                .between(firstDay, today.isBefore(lastDay) ? today : lastDay);

        return book.ledger().totalsByAccount(AccountType.EXPENSE, thatMonth);
    }

    private static MovementQuery queryFrom(MovementFilter filter, Book book) {
        MovementQuery query = MovementQuery.everything()
                .between(filter.from(), filter.to())
                .ofType(filter.type())
                .worth(filter.minAmount(), filter.maxAmount());

        for (AccountId account : filter.accounts()) {
            query = query.touching(book.chart().require(account).id());
        }
        if (filter.person() != null) {
            query = query.withPerson(book.directory().require(filter.person()));
        }
        return query;
    }
}
