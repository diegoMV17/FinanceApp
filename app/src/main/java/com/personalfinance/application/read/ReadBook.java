package com.personalfinance.application.read;

import com.personalfinance.application.BookLoader;
import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.domain.ledger.Book;
import com.personalfinance.domain.ledger.Ledger;
import com.personalfinance.domain.ledger.Person;
import com.personalfinance.domain.ledger.PersonId;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.shared.Money;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

    private final BookLoader loader;

    ReadBook(BookLoader loader) {
        this.loader = loader;
    }

    /** HU22. Cuánto es de verdad suyo, y los totales por tipo, a esa fecha. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BookSummary summary(LocalDate asOf) {
        Ledger ledger = loader.load().ledger();

        Map<AccountType, Money> totals = new EnumMap<>(AccountType.class);
        for (AccountType type : AccountType.values()) {
            totals.put(type, ledger.totalFor(type, asOf));
        }

        return new BookSummary(asOf, ledger.moneyReallyMine(asOf), totals);
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

    /** HU06. La cadena de versiones de un movimiento, la original primero. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<RevisionView> revisionsOf(TransactionId id) {
        return loader.load().ledger().revisionsOf(id).stream()
                .map(RevisionView::of)
                .toList();
    }
}
