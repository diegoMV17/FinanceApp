package com.personalfinance.infrastructure.persistence;

import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.AccountAlreadyArchivedException;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.UnknownAccountException;
import com.personalfinance.domain.ledger.port.AccountStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * El catálogo de cuentas, contra Postgres.
 *
 * <p>No hay {@code delete}: las cuentas se archivan. Lo que cambia después de
 * abrir una cuenta es su nombre y su fecha de archivado, y nada más — por eso
 * esta clase solo sabe hacer esas dos cosas.
 *
 * <p>Abrir dos veces la misma cuenta falla con la clave primaria (ver
 * {@link AssignedIdRow}) en vez de reescribirla: sin eso, "abrir" una cuenta
 * que ya existía podía cambiarle el nombre o desarchivarla sin dejar rastro.
 */
@Repository
class JpaAccountStore implements AccountStore {

    private final AccountRowRepository accounts;

    JpaAccountStore(AccountRowRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional
    public void open(Account account) {
        accounts.saveAndFlush(LedgerMapper.rowOf(account));
    }

    @Override
    @Transactional
    public void markArchived(AccountId id, Instant at) {
        AccountRow row = require(id);
        if (row.isArchived()) {
            throw new AccountAlreadyArchivedException(LedgerMapper.accountFrom(row));
        }
        row.archivedAt(at);
    }

    @Override
    @Transactional
    public void rename(AccountId id, String newName) {
        require(id).renameTo(newName);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> find(AccountId id) {
        return accounts.findById(id.value()).map(LedgerMapper::accountFrom);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> all() {
        return accounts.findAllByOrderByCreatedAtAscIdAsc().stream()
                .map(LedgerMapper::accountFrom)
                .toList();
    }

    /** Devuelve la fila gestionada: cambiarla ya es el UPDATE, al cerrar. */
    private AccountRow require(AccountId id) {
        return accounts.findById(id.value())
                .orElseThrow(() -> new UnknownAccountException(id));
    }
}
