package com.personalfinance.application.usecase;

import com.personalfinance.application.BookLoader;
import com.personalfinance.application.Outcome;
import com.personalfinance.application.command.LinkAccountToPersonCommand;
import com.personalfinance.application.port.AuditRecord;
import com.personalfinance.application.port.AuditTrail;
import com.personalfinance.application.port.BookLock;
import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.Book;
import com.personalfinance.domain.ledger.Person;
import com.personalfinance.domain.ledger.PersonId;
import com.personalfinance.domain.ledger.port.AccountStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU19. Decir de quién es una cuenta.
 *
 * <p>Es el caso que hace aplicable este incremento al libro que ya existe: las
 * cuentas de Juan están abiertas desde antes de que hubiera personas, así que
 * vincular después no es un extra, es el camino principal.
 *
 * <p>La idempotencia aquí no se parece a la de los demás casos, porque no hay
 * un id que el cliente invente: el hecho es «esta cuenta es de esta persona», y
 * eso lo identifican las dos puntas. Mandarlo dos veces responde «ya estaba»;
 * mandar otra persona <em>no</em> es una contradicción sino un traslado, que es
 * la forma de corregir un dedazo sin archivar la cuenta. Por eso este caso no
 * tiene conflicto.
 *
 * <p>READ COMMITTED, como todas las escrituras: ver {@link RecordMovement}.
 */
@Service
public class LinkAccountToPerson {

    private final BookLock lock;
    private final BookLoader loader;
    private final AccountStore accounts;
    private final AuditTrail audit;

    LinkAccountToPerson(
            BookLock lock, BookLoader loader, AccountStore accounts, AuditTrail audit) {

        this.lock = lock;
        this.loader = loader;
        this.accounts = accounts;
        this.audit = audit;
    }

    /**
     * @return la cuenta que ahora es de esa persona
     * @throws com.personalfinance.domain.ledger.AccountCannotBelongToAPersonException
     *         si es una cuenta de un tipo que no es nadie
     * @throws com.personalfinance.domain.ledger.ArchivedAccountException
     *         si la cuenta está archivada
     * @throws com.personalfinance.domain.ledger.ArchivedPersonException
     *         si la persona está archivada
     * @throws com.personalfinance.domain.ledger.UnknownAccountException
     *         si el catálogo no tiene esa cuenta
     * @throws com.personalfinance.domain.ledger.UnknownPersonException
     *         si el directorio no tiene esa persona
     */
    @Transactional
    public Outcome<Account> execute(LinkAccountToPersonCommand command) {
        lock.acquire();
        Book book = loader.load();

        Account account = book.chart().require(command.account());
        Person person = book.directory().require(command.person());

        if (account.belongsTo(person)) {
            return Outcome.alreadyApplied(account);
        }

        PersonId before = account.person().map(Person::id).orElse(null);
        book.chart().linkToPerson(command.account(), person);
        accounts.linkToPerson(command.account(), person.id());
        audit.record(AuditRecord.accountLinked(command.account(), before, person.id()));

        return Outcome.applied(account);
    }
}
