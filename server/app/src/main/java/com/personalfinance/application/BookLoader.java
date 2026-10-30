package com.personalfinance.application;

import com.personalfinance.domain.ledger.Book;
import com.personalfinance.domain.ledger.port.AccountStore;
import com.personalfinance.domain.ledger.port.BudgetStore;
import com.personalfinance.domain.ledger.port.PersonStore;
import com.personalfinance.domain.ledger.port.TransactionStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma el libro completo desde la base: primero las personas, después el
 * catálogo, y sobre él los movimientos y los presupuestos.
 *
 * <p>Ese orden es obligatorio y encadenado. Un apunte guarda el id de su
 * cuenta, no la cuenta, así que sin el catálogo cargado no hay con qué
 * resolverlo; una cuenta guarda el id de su persona, no la persona, así que
 * sin el directorio tampoco; y un presupuesto guarda el id de su categoría,
 * que es otra cuenta. Cargarlo al revés no daría un libro a medias: lo dice en
 * voz alta y no sigue adelante.
 *
 * <p>Si algo no cuadra — un apunte apuntando a una cuenta que no existe, una
 * transacción cuyos apuntes no suman cero — esto falla aquí, al cargar, y no
 * sigue adelante. Un libro corrupto que arranca es un libro que da saldos
 * equivocados en silencio.
 *
 * <p>{@code MANDATORY} a propósito: solo se puede cargar el libro dentro de la
 * transacción de un caso de uso, que es la que fija el aislamiento y, si va a
 * escribir, el candado. Son cuatro consultas — personas, cuentas, movimientos,
 * presupuestos — y la foto tiene que ser la misma para las cuatro: en READ
 * COMMITTED una escritura que se cuele entre ellas deja un apunte apuntando a
 * una cuenta que no se cargó, una cuenta apuntando a una persona que no se
 * cargó o un presupuesto apuntando a una categoría que no se cargó, y la carga
 * revienta con {@code UnknownAccountException} o {@code UnknownPersonException}.
 * Sin esta anotación, cualquiera podría llamarlo por fuera y encontrarse ese
 * fallo sin entender de dónde salió.
 */
@Component
public class BookLoader {

    private final PersonStore people;
    private final AccountStore accounts;
    private final TransactionStore transactions;
    private final BudgetStore budgets;

    BookLoader(
            PersonStore people,
            AccountStore accounts,
            TransactionStore transactions,
            BudgetStore budgets) {

        this.people = people;
        this.accounts = accounts;
        this.transactions = transactions;
        this.budgets = budgets;
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Book load() {
        Book book = Book.empty();

        people.all().forEach(book.directory()::add);
        accounts.all(book.directory()).forEach(book.chart()::add);
        transactions.all(book.chart()).forEach(book.ledger()::record);
        budgets.all(book.chart()).forEach(book.budgets()::add);

        return book;
    }
}
