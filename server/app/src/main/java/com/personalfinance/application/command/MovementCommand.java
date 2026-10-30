package com.personalfinance.application.command;

import com.personalfinance.domain.ledger.Chart;
import com.personalfinance.domain.ledger.Transaction;
import com.personalfinance.domain.ledger.TransactionId;

/**
 * Lo que alguien pide registrar: una de las once operaciones, con sus cuentas
 * por id y su monto.
 *
 * <p>Cada operación es un record propio y cada uno sabe construir su
 * {@link Transaction}. No hay ni un {@code switch} por tipo de operación en
 * ningún lado, y es el mismo motivo por el que no lo hay en el dominio: la
 * diferencia entre un préstamo y una transferencia está en qué cuentas tocan
 * los apuntes, no en un árbol de decisiones repartido por la aplicación.
 *
 * <p>Los comandos son records y su {@code toString} imprime todo lo que traen:
 * descripciones, montos, ids. No se loguean. Si alguna vez hace falta dejar
 * rastro de uno, solo el id.
 *
 * <p>Los límites de contenido — cuánto puede medir una descripción, cuánto un
 * monto — no se validan aquí: viven en el dominio, pegados al dato que
 * protegen, y duplicarlos sería una segunda verdad.
 */
public sealed interface MovementCommand permits
        OpeningBalanceCommand,
        IncomeCommand,
        ExpenseCommand,
        TransferCommand,
        LendCommand,
        CollectLoanCommand,
        BorrowCommand,
        RepayDebtCommand,
        ReceiveThirdPartyMoneyCommand,
        SpendThirdPartyMoneyCommand,
        ReturnThirdPartyMoneyCommand {

    /** El id que trae el cliente, por el que se reconoce un reintento. */
    TransactionId id();

    /**
     * El movimiento armado contra el catálogo que ya está cargado.
     *
     * <p>Las cuentas se resuelven con {@link Chart#require}, así que un id que
     * el catálogo no tiene falla aquí y no más adelante, y la instancia que
     * queda en los apuntes es la misma que el resto del libro usa.
     *
     * @throws com.personalfinance.domain.ledger.UnknownAccountException
     *         si alguna cuenta no está en el catálogo
     */
    Transaction toTransaction(Chart chart);
}
