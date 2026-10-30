package com.personalfinance.application.read;

import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.AccountType;
import com.personalfinance.domain.shared.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * El saldo de una cuenta como lo lee la persona: un fondo de 70.000 muestra
 * 70.000, no −70.000. La conversión la hizo el dominio.
 *
 * <p>Las archivadas también se listan, con su fecha: un reporte de marzo tiene
 * que seguir nombrando la categoría que se dejó de usar en abril.
 *
 * @param archivedAt cuándo se archivó, o {@code null} si sigue abierta
 */
public record AccountBalance(
        AccountId id, AccountType type, String name, Money balance, Instant archivedAt) {

    public AccountBalance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(balance, "balance");
    }

    static AccountBalance of(Account account, Money displayedBalance) {
        return new AccountBalance(
                account.id(), account.type(), account.name(), displayedBalance,
                account.archivedAt().orElse(null));
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public Optional<Instant> archivedOn() {
        return Optional.ofNullable(archivedAt);
    }
}
