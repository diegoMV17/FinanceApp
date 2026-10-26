package com.personalfinance.application;

import com.personalfinance.domain.ledger.Book;
import com.personalfinance.domain.ledger.port.AccountStore;
import com.personalfinance.domain.ledger.port.PersonStore;
import com.personalfinance.domain.ledger.port.TransactionStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma el libro completo desde la base: primero las personas, después el
 * catálogo, al final los movimientos.
 *
 * <p>Ese orden es obligatorio y encadenado. Un apunte guarda el id de su
 * cuenta, no la cuenta, así que sin el catálogo cargado no hay con qué
 * resolverlo; y una cuenta guarda el id de su persona, no la persona, así que
 * sin el directorio tampoco. Cargarlo al revés no daría un libro a medias: lo
 * dice en voz alta y no sigue adelante.
 *
 * <p>Si algo no cuadra — un apunte apuntando a una cuenta que no existe, una
 * transacción cuyos apuntes no suman cero — esto falla aquí, al cargar, y no
 * sigue adelante. Un libro corrupto que arranca es un libro que da saldos
 * equivocados en silencio.
 *
 * <p>{@code MANDATORY} a propósito: solo se puede cargar el libro dentro de la
 * transacción de un caso de uso, que es la que fija el aislamiento y, si va a
 * escribir, el candado. Son tres consultas — personas, cuentas, movimientos — y
 * la foto tiene que ser la misma para las tres: en READ COMMITTED una escritura
 * que se cuele entre ellas deja un apunte apuntando a una cuenta que no se
 * cargó, o una cuenta apuntando a una persona que no se cargó, y la carga
 * revienta con {@code UnknownAccountException} o {@code UnknownPersonException}.
 * Sin esta anotación, cualquiera podría llamarlo por fuera y encontrarse ese
 * fallo sin entender de dónde salió.
 */
@Component
public class BookLoader {

    private final PersonStore people;
    private final AccountStore accounts;
    private final TransactionStore transactions;

    BookLoader(PersonStore people, AccountStore accounts, TransactionStore transactions) {
        this.people = people;
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Book load() {
        Book book = Book.empty();

        people.all().forEach(book.directory()::add);
        accounts.all(book.directory()).forEach(book.chart()::add);
        transactions.all(book.chart()).forEach(book.ledger()::record);

        return book;
    }
}
