package com.personalfinance.application.read;

import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.Entry;
import com.personalfinance.domain.shared.Money;
import java.util.Objects;

/**
 * Un apunte como se muestra: qué cuenta y cuántos centavos, con el signo
 * interno del asiento.
 *
 * <p>Lleva el nombre de la cuenta además del id porque quien lee esto no tiene
 * el catálogo a mano, y porque ese nombre puede haber cambiado desde que el
 * apunte se escribió: el que vale es el de ahora.
 */
public record EntryView(AccountId account, String accountName, Money amount) {

    public EntryView {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(accountName, "accountName");
        Objects.requireNonNull(amount, "amount");
    }

    static EntryView of(Entry entry) {
        return new EntryView(
                entry.account().id(), entry.account().name(), entry.amount());
    }
}
