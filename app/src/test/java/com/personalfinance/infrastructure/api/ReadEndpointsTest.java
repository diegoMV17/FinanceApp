package com.personalfinance.infrastructure.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.personalfinance.application.read.PageRequest;
import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.infrastructure.PostgresIntegrationTest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * H4 y H8. Las seis lecturas, por HTTP.
 *
 * <p>Las lecturas no toman el candado y van en {@code REPEATABLE READ}; eso ya
 * lo prueban los tests {@code C*}. Lo que se prueba aquí es el contrato: qué
 * forma tiene el JSON, qué pasa cuando no se manda la fecha, y que una cuenta
 * archivada siga apareciendo.
 */
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Las lecturas por HTTP")
class ReadEndpointsTest extends PostgresIntegrationTest {

    private static final String JANUARY = "2026-01-15";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;

    private final JsonMapper json = JsonMapper.builder().build();

    private UUID bank;
    private UUID food;
    private UUID equity;
    private UUID expense;

    @BeforeEach
    void writeABookWorthReading() throws Exception {
        jdbc.execute("TRUNCATE entries, transactions, audit_log, accounts, people CASCADE");

        bank = openAccount("ASSET", "Bancolombia");
        food = openAccount("EXPENSE", "Alimentación");
        equity = openAccount("EQUITY", "Saldos iniciales");

        send("/api/movements", """
                {"type":"OPENING_BALANCE","id":"%s","date":"%s","account":"%s",
                 "equity":"%s","amountInCents":80000000}"""
                .formatted(UUID.randomUUID(), JANUARY, bank, equity));

        expense = UUID.randomUUID();
        send("/api/movements", """
                {"type":"EXPENSE","id":"%s","date":"%s","description":"Almuerzo",
                 "paidFrom":"%s","category":"%s","amountInCents":2500000}"""
                .formatted(expense, JANUARY, bank, food));
    }

    @Nested
    @DisplayName("el resumen")
    class TheSummary {

        @Test
        @DisplayName("H4 · trae la plata que de verdad es suya y los siete tipos")
        void bringsTheMoneyAndEveryType() throws Exception {
            JsonNode body = read(ask("/api/summary?asOf=2026-12-31"));

            assertThat(body.get("asOf").asString()).isEqualTo("2026-12-31");
            assertThat(body.get("moneyReallyMineInCents").asLong())
                    .isEqualTo(80_000_000L - 2_500_000L);

            JsonNode totals = body.get("totalsByTypeInCents");
            for (AccountType type : AccountType.values()) {
                assertThat(totals.has(type.name()))
                        .as("falta el tipo %s; un cliente tendría que distinguir "
                                + "'no vino' de 'está en cero'", type)
                        .isTrue();
            }
            assertThat(totals.size()).isEqualTo(AccountType.values().length);
        }

        @Test
        @DisplayName("H4 · los tipos salen en el orden en que están declarados")
        void theTypesComeInDeclarationOrder() throws Exception {
            JsonNode totals = read(ask("/api/summary")).get("totalsByTypeInCents");

            List<String> order = new ArrayList<>();
            totals.propertyNames().forEach(order::add);

            assertThat(order).containsExactly(
                    java.util.Arrays.stream(AccountType.values())
                            .map(Enum::name)
                            .toArray(String[]::new));
        }

        @Test
        @DisplayName("H4 · sin fecha contesta con la de hoy y lo dice")
        void withoutADateItAnswersForToday() throws Exception {
            JsonNode body = read(ask("/api/summary"));

            assertThat(body.get("asOf").asString())
                    .isEqualTo(LocalDate.now(clock).toString());
        }

        @Test
        @DisplayName("H4 · una fecha imposible responde 400, y lo dice del query")
        void anImpossibleDateIs400() throws Exception {
            // El texto importa: «el cuerpo no se pudo leer» en un GET sin
            // cuerpo manda a buscar el error donde no está.
            MvcResult impossible = ask("/api/summary?asOf=2026-13-45");
            assertThat(impossible.getResponse().getStatus()).isEqualTo(400);
            assertThat(read(impossible).get("type").asString())
                    .isEqualTo("urn:finance:error:invalid-path");

            assertThat(ask("/api/summary?asOf=2026-13-45").getResponse().getStatus())
                    .isEqualTo(400);
            assertThat(ask("/api/summary?asOf=ayer").getResponse().getStatus())
                    .isEqualTo(400);
            assertThat(ask("/api/summary?asOf=20261231").getResponse().getStatus())
                    .isEqualTo(400);
            assertThat(ask("/api/summary?asOf=2026-12-31T00:00:00Z")
                            .getResponse().getStatus())
                    .isEqualTo(400);
        }

        @Test
        @DisplayName("H4 · un asOf vacío cuenta como no haberlo mandado")
        void anEmptyAsOfCountsAsAbsent() throws Exception {
            // Spring convierte un parámetro vacío en ausente para un tipo que no
            // es String, y aquí se deja así en vez de forzar un 400. Es seguro
            // por una razón concreta: la respuesta trae el asOf con el que
            // contestó, así que el cliente nunca se queda sin saber de qué día
            // son los números que recibió. Queda escrito como test para que sea
            // una decisión y no una sorpresa.
            MvcResult empty = ask("/api/summary?asOf=");

            assertThat(empty.getResponse().getStatus()).isEqualTo(200);
            assertThat(read(empty).get("asOf").asString())
                    .isEqualTo(LocalDate.now(clock).toString());
        }
    }

    @Nested
    @DisplayName("los saldos")
    class TheBalances {

        @Test
        @DisplayName("H4 · uno por cuenta, con su fecha en el sobre")
        void oneForEachAccount() throws Exception {
            JsonNode body = read(ask("/api/balances?asOf=2026-12-31"));

            assertThat(body.get("asOf").asString()).isEqualTo("2026-12-31");
            JsonNode accounts = body.get("accounts");
            assertThat(accounts.size()).isEqualTo(3);

            JsonNode bancolombia = accountNamed(accounts, "Bancolombia");
            assertThat(bancolombia.get("balanceInCents").asLong())
                    .isEqualTo(80_000_000L - 2_500_000L);
            assertThat(bancolombia.get("type").asString()).isEqualTo("ASSET");
            assertThat(bancolombia.get("archivedAt").isNull()).isTrue();
        }

        @Test
        @DisplayName("H4 · una cuenta archivada sigue saliendo, con su fecha")
        void anArchivedAccountIsStillThere() throws Exception {
            UUID spare = openAccount("ASSET", "Cuenta vieja");
            mvc.perform(post("/api/accounts/" + spare + "/archivals")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_TOKEN));

            JsonNode accounts = read(ask("/api/balances")).get("accounts");

            // Nada se borra en este libro: una cuenta archivada se sigue
            // viendo, y el archivedAt es como se distingue.
            JsonNode archived = accountNamed(accounts, "Cuenta vieja");
            assertThat(archived.get("archivedAt").asString()).endsWith("Z");
        }
    }

    @Nested
    @DisplayName("los movimientos de una cuenta")
    class TheMovementsOfAnAccount {

        @Test
        @DisplayName("H4 · traen el efecto sobre esa cuenta")
        void bringTheEffectOnThatAccount() throws Exception {
            JsonNode body = read(ask("/api/accounts/" + bank + "/movements"));

            assertThat(body.get("account").asString()).isEqualTo(bank.toString());
            JsonNode movements = body.get("movements");
            assertThat(movements.size()).isEqualTo(2);

            JsonNode lunch = movementNamed(movements, "Almuerzo");
            assertThat(lunch.get("effectInCents").asLong())
                    .as("un gasto baja la cuenta de la que salió")
                    .isEqualTo(-2_500_000L);
            assertThat(lunch.get("source").asString()).isEqualTo("APP");
        }

        @Test
        @DisplayName("H4 · una cuenta que no existe responde 404")
        void anUnknownAccountIs404() throws Exception {
            MvcResult missing = ask("/api/accounts/" + UUID.randomUUID() + "/movements");

            assertThat(missing.getResponse().getStatus()).isEqualTo(404);
            assertThat(read(missing).get("type").asString())
                    .isEqualTo("urn:finance:error:not-found");
        }

        @Test
        @DisplayName("H4 · un id que no es un UUID responde 400, no 404")
        void aNonUuidIs400() throws Exception {
            // Es un 400 y no un 404 porque el problema no es que no exista:
            // es que eso no es un identificador.
            MvcResult notAnId = ask("/api/accounts/no-soy-un-uuid/movements");

            assertThat(notAnId.getResponse().getStatus()).isEqualTo(400);
            assertThat(read(notAnId).get("type").asString())
                    .as("con su propio código: lo que falló fue la ruta, no el cuerpo")
                    .isEqualTo("urn:finance:error:invalid-path");
        }

        @Test
        @DisplayName("H9 · la página y el tamaño van en el sobre, con el total sin cortar")
        void paginatesAndSaysTheTotal() throws Exception {
            JsonNode first = read(ask("/api/accounts/" + bank + "/movements?page=0&size=1"));

            assertThat(first.get("movements").size()).isEqualTo(1);
            assertThat(first.get("page").asInt()).isZero();
            assertThat(first.get("size").asInt()).isEqualTo(1);
            assertThat(first.get("total").asLong())
                    .as("el total es el del libro, no el de la página")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("H9 · sin página contesta la primera, con el tamaño por omisión")
        void defaultsToTheFirstPage() throws Exception {
            JsonNode body = read(ask("/api/accounts/" + bank + "/movements"));

            assertThat(body.get("page").asInt()).isZero();
            assertThat(body.get("size").asInt()).isEqualTo(PageRequest.DEFAULT_SIZE);
            assertThat(body.get("movements").size()).isEqualTo(2);
        }

        /**
         * El tope existe para que una petición corta no pueda pedir una
         * respuesta enorme. Que el borde conteste 400 en vez de recortar en
         * silencio es lo que le dice a un programa que pidió algo que no se
         * admite; el recorte de {@code PageRequest} es la red de atrás.
         */
        @Test
        @DisplayName("H9 · un tamaño por encima del tope responde 400")
        void aSizeOverTheCapIs400() throws Exception {
            MvcResult tooBig = ask("/api/accounts/" + bank + "/movements?size="
                    + (PageRequest.MAX_SIZE + 1));

            assertThat(tooBig.getResponse().getStatus()).isEqualTo(400);
            assertThat(read(tooBig).get("type").asString())
                    .as("un parámetro fuera de rango no es un cuerpo ilegible")
                    .isEqualTo("urn:finance:error:invalid-request");
        }

        @Test
        @DisplayName("H9 · una página más allá del final es una lista vacía, no un 404")
        void pastTheEndIsEmpty() throws Exception {
            JsonNode body = read(ask("/api/accounts/" + bank + "/movements?page=9"));

            assertThat(body.get("movements")).isEmpty();
            assertThat(body.get("total").asLong()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("las revisiones de un movimiento")
    class TheRevisionsOfAMovement {

        @Test
        @DisplayName("H4 · traen la cadena completa de una corrección")
        void bringTheWholeChain() throws Exception {
            UUID correction = UUID.randomUUID();
            send("/api/movements/" + expense + "/amendments", """
                    {"correction":{"type":"EXPENSE","id":"%s","date":"%s",
                     "description":"Almuerzo corregido","paidFrom":"%s","category":"%s",
                     "amountInCents":3000000}}"""
                    .formatted(correction, JANUARY, bank, food));

            JsonNode body = read(ask("/api/movements/" + expense + "/revisions"));

            assertThat(body.get("movement").asString()).isEqualTo(expense.toString());
            JsonNode revisions = body.get("revisions");
            assertThat(revisions.size()).isEqualTo(2);

            JsonNode original = revisions.get(0);
            assertThat(original.get("id").asString()).isEqualTo(expense.toString());
            assertThat(original.get("cancelledAt").asString()).endsWith("Z");
            assertThat(original.get("cancellationReason").isNull()).isFalse();

            JsonNode fixed = revisions.get(1);
            assertThat(fixed.get("id").asString()).isEqualTo(correction.toString());
            assertThat(fixed.get("replaces").asString()).isEqualTo(expense.toString());
            assertThat(fixed.get("entries").size()).isEqualTo(2);
        }

        @Test
        @DisplayName("H4 · un movimiento sin correcciones trae solo una")
        void anUntouchedMovementHasOneRevision() throws Exception {
            JsonNode revisions = read(ask("/api/movements/" + expense + "/revisions"))
                    .get("revisions");

            assertThat(revisions.size()).isEqualTo(1);
            assertThat(revisions.get(0).get("cancelledAt").isNull()).isTrue();
            assertThat(revisions.get(0).get("replaces").isNull()).isTrue();
        }

        @Test
        @DisplayName("H4 · un movimiento que no existe responde 404")
        void anUnknownMovementIs404() throws Exception {
            assertThat(ask("/api/movements/" + UUID.randomUUID() + "/revisions")
                            .getResponse().getStatus())
                    .isEqualTo(404);
        }
    }

    /**
     * H8. HU19 y HU21: la lectura por la que existe el incremento.
     *
     * <p>El libro de este nido es el ejemplo de siempre: Juan me debe 100.000,
     * yo le debo 30.000 y tengo 50.000 de su plata guardados. Los tres números
     * salen en positivo y el neto da 20.000.
     */
    @Nested
    @DisplayName("las personas")
    class ThePeople {

        private UUID juan;
        private UUID owedByJuan;
        private UUID owedToJuan;
        private UUID juansFund;

        @BeforeEach
        void aBookWithJuanInIt() throws Exception {
            juan = addPerson("Juan");

            owedByJuan = openAccount("RECEIVABLE", "Juan");
            owedToJuan = openAccount("PAYABLE", "Juan");
            juansFund = openAccount("THIRD_PARTY_FUND", "Juan");
            link(juan, owedByJuan);
            link(juan, owedToJuan);
            link(juan, juansFund);

            send("/api/movements", """
                    {"type":"LEND","id":"%s","date":"%s","description":"Préstamo",
                     "paidFrom":"%s","borrower":"%s","amountInCents":10000000}"""
                    .formatted(UUID.randomUUID(), JANUARY, bank, owedByJuan));
            send("/api/movements", """
                    {"type":"BORROW","id":"%s","date":"%s","description":"Me prestó",
                     "lender":"%s","receivedInto":"%s","amountInCents":3000000}"""
                    .formatted(UUID.randomUUID(), JANUARY, owedToJuan, bank));
            send("/api/movements", """
                    {"type":"RECEIVE_THIRD_PARTY_MONEY","id":"%s","date":"%s",
                     "description":"Me dejó plata","fund":"%s","heldIn":"%s",
                     "amountInCents":5000000}"""
                    .formatted(UUID.randomUUID(), JANUARY, juansFund, bank));
        }

        @Test
        @DisplayName("H8 · traen los tres lados y el neto, con el signo que se lee")
        void theThreeSidesAndTheNet() throws Exception {
            JsonNode body = read(ask("/api/people?asOf=2026-12-31"));

            assertThat(body.get("asOf").asString()).isEqualTo("2026-12-31");
            JsonNode person = personNamed(body.get("people"), "Juan");

            assertThat(person.get("id").asString()).isEqualTo(juan.toString());
            assertThat(person.get("owedToYouInCents").asLong()).isEqualTo(10_000_000L);
            assertThat(person.get("youOweInCents").asLong()).isEqualTo(3_000_000L);
            assertThat(person.get("theirFundInCents").asLong()).isEqualTo(5_000_000L);
            assertThat(person.get("netOwedToYouInCents").asLong()).isEqualTo(2_000_000L);
            assertThat(person.get("archivedAt").isNull()).isTrue();
        }

        @Test
        @DisplayName("H8 · y las cuentas de las que salen esos números, con su saldo")
        void theAccountsBehindTheNumbers() throws Exception {
            JsonNode accounts = read(ask("/api/people/" + juan + "?asOf=2026-12-31"))
                    .get("person").get("accounts");

            assertThat(accounts.size()).isEqualTo(3);
            assertThat(accountNamed(accounts, "Juan").get("balanceInCents").asLong())
                    .isPositive();

            List<String> types = new ArrayList<>();
            accounts.forEach(account -> types.add(account.get("type").asString()));
            assertThat(types).containsExactlyInAnyOrder(
                    "RECEIVABLE", "PAYABLE", "THIRD_PARTY_FUND");
        }

        @Test
        @DisplayName("H8 · una sola persona viene en su sobre, con el asOf")
        void onePersonComesInAnEnvelope() throws Exception {
            JsonNode body = read(ask("/api/people/" + juan + "?asOf=2026-12-31"));

            assertThat(body.get("asOf").asString()).isEqualTo("2026-12-31");
            assertThat(body.get("person").get("name").asString()).isEqualTo("Juan");
        }

        @Test
        @DisplayName("H8 · sin fecha contestan con la de hoy y lo dicen")
        void withoutADateTheyAnswerForToday() throws Exception {
            String today = LocalDate.now(clock).toString();

            assertThat(read(ask("/api/people")).get("asOf").asString()).isEqualTo(today);
            assertThat(read(ask("/api/people/" + juan)).get("asOf").asString())
                    .isEqualTo(today);
        }

        @Test
        @DisplayName("H8 · un asOf vacío cuenta como ausente, y la respuesta lo dice")
        void anEmptyAsOfCountsAsAbsent() throws Exception {
            JsonNode body = read(ask("/api/people?asOf="));

            assertThat(body.get("asOf").asString()).isEqualTo(LocalDate.now(clock).toString());
        }

        @Test
        @DisplayName("H8 · antes del primer movimiento la posición está en cero")
        void beforeAnythingHappenedEverythingIsZero() throws Exception {
            JsonNode person = read(ask("/api/people/" + juan + "?asOf=2025-12-31"))
                    .get("person");

            assertThat(person.get("owedToYouInCents").asLong()).isZero();
            assertThat(person.get("youOweInCents").asLong()).isZero();
            assertThat(person.get("theirFundInCents").asLong()).isZero();
            assertThat(person.get("netOwedToYouInCents").asLong()).isZero();
        }

        @Test
        @DisplayName("H8 · una persona sin cuentas sale en cero, no se omite")
        void aPersonWithoutAccountsStillComes() throws Exception {
            UUID pedro = addPerson("Pedro");

            JsonNode person = personNamed(read(ask("/api/people")).get("people"), "Pedro");

            assertThat(person.get("id").asString()).isEqualTo(pedro.toString());
            assertThat(person.get("netOwedToYouInCents").asLong()).isZero();
            assertThat(person.get("accounts").size()).isZero();
        }

        @Test
        @DisplayName("H8 · una persona que no existe responde 404")
        void anUnknownPersonIs404() throws Exception {
            assertThat(ask("/api/people/" + UUID.randomUUID()).getResponse().getStatus())
                    .isEqualTo(404);
        }

        private UUID addPerson(String name) throws Exception {
            UUID id = UUID.randomUUID();
            send("/api/people", """
                    {"id":"%s","name":"%s"}""".formatted(id, name));
            return id;
        }

        private void link(UUID person, UUID account) throws Exception {
            send("/api/people/" + person + "/accounts", """
                    {"account":"%s"}""".formatted(account));
        }
    }

    /**
     * H10, H11, H12. HU24 y HU25: el historial general y sus siete filtros.
     *
     * <p>Al libro de siempre — un saldo inicial y un almuerzo en enero — este
     * nido le añade un sueldo y un bus en febrero y un préstamo a Juan en
     * marzo, porque un filtro no se puede probar contra un libro que solo tiene
     * un mes, una categoría y ninguna persona.
     */
    @Nested
    @DisplayName("el historial general")
    class TheGeneralHistory {

        private static final String FEBRUARY = "2026-02-10";
        private static final String MARCH = "2026-03-05";

        private UUID transport;
        private UUID bus;
        private UUID payday;
        private UUID loan;
        private UUID juan;

        @BeforeEach
        void addMoreMonths() throws Exception {
            transport = openAccount("EXPENSE", "Transporte");
            UUID salary = openAccount("INCOME", "Salario");
            UUID owedByJuan = openAccount("RECEIVABLE", "Juan");

            juan = UUID.randomUUID();
            send("/api/people", """
                    {"id":"%s","name":"Juan"}""".formatted(juan));
            send("/api/people/" + juan + "/accounts", """
                    {"account":"%s"}""".formatted(owedByJuan));

            payday = UUID.randomUUID();
            send("/api/movements", """
                    {"type":"INCOME","id":"%s","date":"%s","description":"Sueldo",
                     "receivedInto":"%s","category":"%s","amountInCents":400000000}"""
                    .formatted(payday, FEBRUARY, bank, salary));

            bus = UUID.randomUUID();
            send("/api/movements", """
                    {"type":"EXPENSE","id":"%s","date":"%s","description":"Bus",
                     "paidFrom":"%s","category":"%s","amountInCents":500000}"""
                    .formatted(bus, FEBRUARY, bank, transport));

            loan = UUID.randomUUID();
            send("/api/movements", """
                    {"type":"LEND","id":"%s","date":"%s","description":"Préstamo",
                     "paidFrom":"%s","borrower":"%s","amountInCents":30000000}"""
                    .formatted(loan, MARCH, bank, owedByJuan));
        }

        @Test
        @DisplayName("H10 · sin filtros trae todo, con los apuntes y el valor de cada uno")
        void unfilteredBringsEverything() throws Exception {
            JsonNode body = read(ask("/api/movements"));

            assertThat(body.get("total").asLong()).isEqualTo(5);
            assertThat(body.get("page").asInt()).isZero();

            JsonNode lunch = movementNamed(body.get("movements"), "Almuerzo");
            assertThat(lunch.get("valueInCents").asLong())
                    .as("el valor de un movimiento es su magnitud, no un apunte suelto")
                    .isEqualTo(2_500_000L);
            assertThat(lunch.get("entries").size())
                    .as("sin cuenta desde la que mirar, lo que explica el movimiento "
                            + "son sus apuntes")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("H10 · el rango de fechas acota, y los dos extremos están dentro")
        void theDateRangeBoundsBothEnds() throws Exception {
            JsonNode february = read(ask("/api/movements?from=2026-02-01&to=2026-02-28"));

            assertThat(february.get("total").asLong()).isEqualTo(2);

            JsonNode justTheTenth = read(ask("/api/movements?from=" + FEBRUARY
                    + "&to=" + FEBRUARY));
            assertThat(justTheTenth.get("total").asLong())
                    .as("un rango de un solo día es un día, no un error")
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("H10 · el tipo acota a los movimientos que tocan ese tipo")
        void theTypeBounds() throws Exception {
            JsonNode expenses = read(ask("/api/movements?type=EXPENSE"));

            assertThat(expenses.get("total").asLong()).isEqualTo(2);
            assertThat(expenses.get("movements").size()).isEqualTo(2);
        }

        /**
         * La consulta por la que el filtro vale la pena: lo que salió de una
         * cuenta hacia una categoría. Dos cuentas significan las dos, no
         * cualquiera de las dos: con «cualquiera» la respuesta serían todos los
         * gastos de esa categoría más todo lo que alguna vez salió de esa
         * cuenta, que no es una pregunta que nadie haga.
         */
        @Test
        @DisplayName("H10 · dos cuentas significan las dos, no cualquiera de las dos")
        void twoAccountsMeanBoth() throws Exception {
            JsonNode both = read(ask(
                    "/api/movements?account=" + bank + "&account=" + transport));

            assertThat(both.get("total").asLong()).isEqualTo(1);
            assertThat(both.get("movements").get(0).get("description").asString())
                    .isEqualTo("Bus");
        }

        @Test
        @DisplayName("H10 · la persona trae lo que toca alguna cuenta suya")
        void thePersonBringsTheirs() throws Exception {
            JsonNode withJuan = read(ask("/api/movements?person=" + juan));

            assertThat(withJuan.get("total").asLong()).isEqualTo(1);
            assertThat(withJuan.get("movements").get(0).get("id").asString())
                    .isEqualTo(loan.toString());
        }

        @Test
        @DisplayName("H10 · el rango de valor acota por la magnitud")
        void theValueRangeBounds() throws Exception {
            JsonNode small = read(ask("/api/movements?maxInCents=1000000"));

            assertThat(small.get("total").asLong()).isEqualTo(1);
            assertThat(small.get("movements").get(0).get("id").asString())
                    .isEqualTo(bus.toString());

            JsonNode exact = read(ask("/api/movements?minInCents=500000&maxInCents=500000"));
            assertThat(exact.get("total").asLong())
                    .as("los dos extremos del valor están dentro")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("H10 · los filtros se combinan entre ellos")
        void theFiltersCombine() throws Exception {
            JsonNode none = read(ask("/api/movements?type=EXPENSE&person=" + juan));

            assertThat(none.get("total").asLong())
                    .as("el préstamo a Juan no es un gasto")
                    .isZero();
        }

        /**
         * Una lista vacía se lee como «no hubo movimientos», que es una
         * respuesta perfectamente tranquilizadora a una pregunta mal escrita.
         */
        @Test
        @DisplayName("H11 · una cuenta que no existe responde 404, no una lista vacía")
        void anUnknownAccountIs404() throws Exception {
            MvcResult missing = ask("/api/movements?account=" + UUID.randomUUID());

            assertThat(missing.getResponse().getStatus()).isEqualTo(404);
            assertThat(read(missing).get("type").asString())
                    .isEqualTo("urn:finance:error:not-found");
        }

        @Test
        @DisplayName("H11 · una persona que no existe responde 404 igual")
        void anUnknownPersonIs404() throws Exception {
            assertThat(ask("/api/movements?person=" + UUID.randomUUID())
                            .getResponse().getStatus())
                    .isEqualTo(404);
        }

        @Test
        @DisplayName("H12 · un rango de fechas al revés responde 422 y dice cuál es")
        void aReversedDateRangeIs422() throws Exception {
            MvcResult reversed = ask("/api/movements?from=2026-03-31&to=2026-03-01");

            assertThat(reversed.getResponse().getStatus()).isEqualTo(422);
            assertThat(read(reversed).get("type").asString())
                    .as("es el libro diciendo que no, no un cuerpo ilegible")
                    .isEqualTo("urn:finance:error:reversed-range");
        }

        @Test
        @DisplayName("H12 · un rango de valor al revés responde 422 también")
        void aReversedValueRangeIs422() throws Exception {
            MvcResult reversed = ask("/api/movements?minInCents=900000&maxInCents=1000");

            assertThat(reversed.getResponse().getStatus()).isEqualTo(422);
            assertThat(read(reversed).get("type").asString())
                    .isEqualTo("urn:finance:error:reversed-range");
        }

        @Test
        @DisplayName("H12 · una fecha fuera del siglo del libro responde 422")
        void aDateOutOfTheCenturyIs422() throws Exception {
            MvcResult tooOld = ask("/api/movements?from=1999-12-31");

            assertThat(tooOld.getResponse().getStatus()).isEqualTo(422);
            assertThat(read(tooOld).get("type").asString())
                    .isEqualTo("urn:finance:error:date-out-of-range");
        }

        @Test
        @DisplayName("H12 · un valor por encima del tope responde 400 en el borde")
        void aValueOverTheCapIs400() throws Exception {
            MvcResult tooBig = ask("/api/movements?minInCents=9999999999999999");

            assertThat(tooBig.getResponse().getStatus()).isEqualTo(400);
            assertThat(read(tooBig).get("type").asString())
                    .isEqualTo("urn:finance:error:invalid-request");
        }

        @Test
        @DisplayName("H12 · un tipo inventado responde 400, y lo dice del query")
        void anInventedTypeIs400() throws Exception {
            MvcResult nonsense = ask("/api/movements?type=BANANA");

            assertThat(nonsense.getResponse().getStatus()).isEqualTo(400);
            assertThat(read(nonsense).get("type").asString())
                    .isEqualTo("urn:finance:error:invalid-path");
        }

        @Test
        @DisplayName("H12 · lo anulado no sale en el historial")
        void cancelledMovementsAreNotInTheHistory() throws Exception {
            send("/api/movements/" + bus + "/cancellations", """
                    {"reason":"No fue"}""");

            JsonNode body = read(ask("/api/movements"));

            assertThat(body.get("total").asLong()).isEqualTo(4);
            assertThat(body.toString()).doesNotContain(bus.toString());
        }
    }

    private static JsonNode personNamed(JsonNode people, String name) {
        for (JsonNode person : people) {
            if (name.equals(person.get("name").asString())) {
                return person;
            }
        }
        throw new AssertionError("no vino la persona " + name);
    }

    private static JsonNode accountNamed(JsonNode accounts, String name) {
        for (JsonNode account : accounts) {
            if (name.equals(account.get("name").asString())) {
                return account;
            }
        }
        throw new AssertionError("no vino la cuenta " + name);
    }

    private static JsonNode movementNamed(JsonNode movements, String description) {
        for (JsonNode movement : movements) {
            if (description.equals(movement.get("description").asString())) {
                return movement;
            }
        }
        throw new AssertionError("no vino el movimiento " + description);
    }

    private UUID openAccount(String type, String name) throws Exception {
        UUID id = UUID.randomUUID();
        send("/api/accounts",
                """
                {"id":"%s","type":"%s","name":"%s"}""".formatted(id, type, name));
        return id;
    }

    private MvcResult ask(String path) throws Exception {
        return mvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_TOKEN))
                .andReturn();
    }

    private void send(String path, String body) throws Exception {
        MvcResult result = mvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .as("montando el libro con %s: %s", path,
                        result.getResponse().getContentAsString())
                .isEqualTo(201);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }
}
