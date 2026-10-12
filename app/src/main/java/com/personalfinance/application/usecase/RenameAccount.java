package com.personalfinance.application.usecase;

import com.personalfinance.application.BookLoader;
import com.personalfinance.application.Outcome;
import com.personalfinance.application.command.RenameAccountCommand;
import com.personalfinance.application.port.AuditTrail;
import com.personalfinance.application.port.BookLock;
import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.port.AccountStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambiarle el nombre a una cuenta.
 *
 * <p>Es el único caso donde la bitácora guarda contenido: la tabla
 * {@code accounts} solo conserva el nombre de ahora, así que sin el anterior no
 * quedaría cómo se llamaba antes. Nada más se copia a la auditoría.
 *
 * <p>Pedir el nombre que ya tiene — con la misma regla con que el catálogo
 * compara nombres, sin distinguir mayúsculas ni espacios de los extremos — es
 * un reintento y no escribe nada.
 *
 * <p>READ COMMITTED, como todas las escrituras: ver {@link RecordMovement}.
 */
@Service
public class RenameAccount {

    private final BookLock lock;
    private final BookLoader loader;
    private final AccountStore accounts;
    private final AuditTrail audit;

    RenameAccount(BookLock lock, BookLoader loader, AccountStore accounts, AuditTrail audit) {
        this.lock = lock;
        this.loader = loader;
        this.accounts = accounts;
        this.audit = audit;
    }

    /**
     * @return la cuenta con el nombre que quedó
     * @throws com.personalfinance.domain.ledger.DuplicateAccountNameException
     *         si ese nombre ya lo tiene otra cuenta abierta del mismo tipo
     * @throws com.personalfinance.domain.ledger.UnknownAccountException
     *         si el catálogo no tiene esa cuenta
     */
    @Transactional
    public Outcome<Account> execute(RenameAccountCommand command) {
        throw new UnsupportedOperationException("paso 4");
    }
}
