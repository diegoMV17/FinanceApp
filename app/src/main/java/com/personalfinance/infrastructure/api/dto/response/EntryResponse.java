package com.personalfinance.infrastructure.api.dto.response;

import com.personalfinance.application.read.EntryView;
import com.personalfinance.domain.ledger.Entry;
import java.util.UUID;

/**
 * Un apunte: una cuenta y lo que le pasa.
 *
 * <p>El monto lleva el signo interno del asiento, que es el que suma cero con
 * sus hermanos. No es el signo de presentación: aquí no se está mostrando un
 * saldo, se está mostrando la partida doble tal como quedó escrita.
 */
public record EntryResponse(UUID account, String accountName, long amountInCents) {

    public static EntryResponse of(Entry entry) {
        return new EntryResponse(
                entry.account().id().value(),
                entry.account().name(),
                entry.amount().cents());
    }

    public static EntryResponse of(EntryView entry) {
        return new EntryResponse(
                entry.account().value(), entry.accountName(), entry.amount().cents());
    }
}
