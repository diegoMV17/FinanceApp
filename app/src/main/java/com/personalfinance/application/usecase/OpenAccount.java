package com.personalfinance.application.usecase;

import com.personalfinance.application.BookLoader;
import com.personalfinance.application.Outcome;
import com.personalfinance.application.command.OpenAccountCommand;
import com.personalfinance.application.port.AuditRecord;
import com.personalfinance.application.port.AuditTrail;
import com.personalfinance.application.port.BookLock;
import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.Chart;
import com.personalfinance.domain.ledger.DuplicateAccountException;
import com.personalfinance.domain.ledger.port.AccountStore;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU08. Abrir una cuenta, una persona o una categoría.
 *
 * <p>El mismo id con el mismo contenido es un reintento. El mismo id con otro
 * contenido es una contradicción, y otro id con un nombre que ya está en uso
 * entre las abiertas del mismo tipo lo rechaza el dominio: elegir "Nequi" de
 * una lista que lo tiene dos veces es una moneda al aire.
 *
 * <p>READ COMMITTED, como todas las escrituras: ver {@link RecordMovement}.
 */
@Service
public class OpenAccount {

    private final BookLock lock;
    private final BookLoader loader;
    private final AccountStore accounts;
    private final AuditTrail audit;

    OpenAccount(BookLock lock, BookLoader loader, AccountStore accounts, AuditTrail audit) {
        this.lock = lock;
        this.loader = loader;
        this.accounts = accounts;
        this.audit = audit;
    }

    /**
     * @return la cuenta que quedó en el catálogo
     * @throws com.personalfinance.domain.ledger.DuplicateAccountException
     *         si ese id ya está en el catálogo con otro contenido
     * @throws com.personalfinance.domain.ledger.DuplicateAccountNameException
     *         si ya hay una cuenta abierta de ese tipo con ese nombre
     */
    @Transactional
    public Outcome<Account> execute(OpenAccountCommand command) {
        lock.acquire();
        Chart chart = loader.load().chart();

        Optional<Account> stored = chart.find(command.id());
        if (stored.isPresent()) {
            return sameOrConflict(stored.get(), command);
        }

        Account opened = chart.open(command.id(), command.type(), command.name());
        accounts.open(opened);
        audit.record(AuditRecord.accountOpened(opened.id()));

        return Outcome.applied(opened);
    }

    /**
     * La cuenta que se pidió se construye solo para compararla: es la forma de
     * que el nombre llegue recortado y con los mismos límites que tendría si se
     * fuera a guardar, y la comparación use la misma regla de nombres que el
     * catálogo.
     */
    private static Outcome<Account> sameOrConflict(Account stored, OpenAccountCommand command) {
        Account asked = Account.open(command.id(), command.type(), command.name());
        if (stored.sameContentAs(asked)) {
            return Outcome.alreadyApplied(stored);
        }
        throw new DuplicateAccountException(command.id());
    }
}
