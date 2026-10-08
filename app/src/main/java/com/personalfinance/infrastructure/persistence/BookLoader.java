package com.personalfinance.infrastructure.persistence;

import com.personalfinance.domain.ledger.Book;
import com.personalfinance.domain.ledger.port.AccountStore;
import com.personalfinance.domain.ledger.port.TransactionStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma el libro completo desde la base: primero el catálogo, después los
 * movimientos.
 *
 * <p>Ese orden es obligatorio. Un apunte guarda el id de su cuenta, no la
 * cuenta, así que sin el catálogo cargado no hay con qué resolverlo.
 *
 * <p>Si algo no cuadra — un apunte apuntando a una cuenta que no existe, una
 * transacción cuyos apuntes no suman cero — esto falla aquí, al cargar, y no
 * sigue adelante. Un libro corrupto que arranca es un libro que da saldos
 * equivocados en silencio.
 */
@Component
public class BookLoader {

    private final AccountStore accounts;
    private final TransactionStore transactions;

    BookLoader(AccountStore accounts, TransactionStore transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public Book load() {
        Book book = Book.empty();

        accounts.all().forEach(book.chart()::add);
        transactions.all(book.chart()).forEach(book.ledger()::record);

        return book;
    }
}
