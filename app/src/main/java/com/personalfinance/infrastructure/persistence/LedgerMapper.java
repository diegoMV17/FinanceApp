package com.personalfinance.infrastructure.persistence;

import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.Cancellation;
import com.personalfinance.domain.ledger.Chart;
import com.personalfinance.domain.ledger.Entry;
import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.shared.Money;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Traduce entre las filas y el dominio, en los dos sentidos.
 *
 * <p>Vive fuera del paquete del dominio, así que solo puede usar su API
 * pública: las factorías {@code rehydrated(...)}. Eso es a propósito — el
 * mapeador no tiene forma de saltarse una invariante ni por descuido.
 *
 * <p>Al cargar, cada apunte resuelve su cuenta contra el {@link Chart} que se
 * le pasa, nunca construyendo una suya. Dos objetos {@code Account} llamados
 * "Bancolombia" con ids distintos son dos cuentas para el libro, y el saldo se
 * partiría en dos sin que nadie se diera cuenta.
 */
final class LedgerMapper {

    static AccountRow rowOf(Account account) {
        return new AccountRow(
                account.id().value(),
                account.type(),
                account.name(),
                account.archivedAt().orElse(null));
    }

    static Account accountFrom(AccountRow row) {
        return Account.rehydrated(
                new AccountId(row.getId()), row.type(), row.name(), row.archivedAt());
    }

    static TransactionRow rowOf(Transaction transaction) {
        return new TransactionRow(
                transaction.id().value(),
                transaction.date(),
                transaction.description(),
                transaction.source(),
                transaction.replaces().map(TransactionId::value).orElse(null));
    }

    /**
     * Los apuntes de una transacción, listos para insertar. El id lo genera
     * aquí: la tabla necesita clave primaria y el dominio no tiene ninguna que
     * dar, porque un apunte es un valor dentro de su transacción.
     */
    static List<EntryRow> rowsOf(Transaction transaction) {
        List<Entry> entries = transaction.entries();
        List<EntryRow> rows = new ArrayList<>(entries.size());

        for (short position = 0; position < entries.size(); position++) {
            Entry entry = entries.get(position);
            rows.add(new EntryRow(
                    UUID.randomUUID(),
                    transaction.id().value(),
                    entry.account().id().value(),
                    entry.amount().cents(),
                    position));
        }
        return rows;
    }

    /**
     * @param chart el catálogo ya cargado; si un apunte apunta a una cuenta que
     *              no está, {@code Chart.require} lo dice en voz alta en vez de
     *              dejar el libro a medias
     */
    static Transaction transactionFrom(TransactionRow row, List<EntryRow> entryRows, Chart chart) {
        List<Entry> entries = entryRows.stream()
                .sorted(Comparator.comparingInt(EntryRow::position))
                .map(entry -> Entry.rehydrated(
                        chart.require(new AccountId(entry.accountId())),
                        Money.ofCents(entry.amountCents())))
                .toList();

        return Transaction.rehydrated(
                new TransactionId(row.getId()),
                row.occurredOn(),
                row.description(),
                row.source(),
                entries,
                row.replacesId() == null ? null : new TransactionId(row.replacesId()),
                cancellationFrom(row));
    }

    private static Cancellation cancellationFrom(TransactionRow row) {
        if (row.cancelledAt() == null) {
            return null;
        }
        return new Cancellation(
                row.cancelledAt(), row.cancellationNote(), row.cancellationSource());
    }

    private LedgerMapper() {
    }
}
