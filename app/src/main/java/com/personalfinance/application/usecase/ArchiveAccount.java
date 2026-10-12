package com.personalfinance.application.usecase;

import com.personalfinance.application.BookLoader;
import com.personalfinance.application.Outcome;
import com.personalfinance.application.command.ArchiveAccountCommand;
import com.personalfinance.application.port.AuditRecord;
import com.personalfinance.application.port.AuditTrail;
import com.personalfinance.application.port.BookLock;
import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.Book;
import com.personalfinance.domain.ledger.port.AccountStore;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU11. Archivar una cuenta.
 *
 * <p>Este es el caso que explica por qué el candado existe. Archivar exige que
 * el saldo eventual de la cuenta sea cero, y esa regla cruza todas las filas
 * del libro: sin turno, una petición que registra un movimiento en la cuenta
 * mientras esta decide deja una cuenta archivada con plata adentro, y archivar
 * no se deshace.
 *
 * <p>Una cuenta ya archivada responde "ya estaba" con su fecha original: un
 * archivado no tiene más contenido que comparar.
 *
 * <p>READ COMMITTED, como todas las escrituras: ver {@link RecordMovement}.
 */
@Service
public class ArchiveAccount {

    private final BookLock lock;
    private final BookLoader loader;
    private final AccountStore accounts;
    private final AuditTrail audit;
    private final Clock clock;

    ArchiveAccount(
            BookLock lock,
            BookLoader loader,
            AccountStore accounts,
            AuditTrail audit,
            Clock clock) {

        this.lock = lock;
        this.loader = loader;
        this.accounts = accounts;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * @return la cuenta archivada, con la fecha en que lo fue
     * @throws com.personalfinance.domain.ledger.AccountStillHoldsMoneyException
     *         si todavía tiene plata, contando los movimientos programados
     * @throws com.personalfinance.domain.ledger.UnknownAccountException
     *         si el catálogo no tiene esa cuenta
     */
    @Transactional
    public Outcome<Account> execute(ArchiveAccountCommand command) {
        lock.acquire();
        Book book = loader.load();

        Account account = book.chart().require(command.id());
        if (account.isArchived()) {
            return Outcome.alreadyApplied(account);
        }

        // La misma fecha en el libro y en la base. Dos llamadas al reloj darían
        // dos fechas distintas para el mismo hecho, y el archivado no se
        // reescribe después.
        Instant when = clock.instant();
        book.chart().archive(command.id(), book.ledger(), when);
        accounts.markArchived(command.id(), when);
        audit.record(AuditRecord.accountArchived(command.id()));

        return Outcome.applied(account);
    }
}
