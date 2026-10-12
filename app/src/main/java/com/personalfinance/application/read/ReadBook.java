package com.personalfinance.application.read;

import com.personalfinance.application.BookLoader;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.TransactionId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Todo lo que se lee del libro.
 *
 * <p>Las lecturas no toman el candado: no escriben nada y hacerlas esperar
 * detrás de una escritura no protege nada. Lo que sí necesitan es
 * {@code REPEATABLE_READ}. {@link BookLoader} hace dos consultas — primero el
 * catálogo, después los movimientos — y en READ COMMITTED una escritura que se
 * cuele entre las dos deja un apunte apuntando a una cuenta que no se cargó: la
 * carga revienta con {@code UnknownAccountException} y la lectura falla sin que
 * nada esté mal.
 *
 * <p>Devuelve records de esta capa y nunca entidades de persistencia. Tampoco
 * devuelve objetos del dominio: un {@code Account} tiene mutadores que solo el
 * agregado debe poder llamar, y lo que sale de una lectura va camino a un JSON.
 */
@Service
public class ReadBook {

    private final BookLoader loader;

    ReadBook(BookLoader loader) {
        this.loader = loader;
    }

    /** HU22. Cuánto es de verdad suyo, y los totales por tipo, a esa fecha. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BookSummary summary(LocalDate asOf) {
        throw new UnsupportedOperationException("paso 4");
    }

    /** El saldo de cada cuenta, archivadas incluidas, en el orden en que se abrieron. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<AccountBalance> balances(LocalDate asOf) {
        throw new UnsupportedOperationException("paso 4");
    }

    /** HU21, HU24. Los movimientos que siguen contando sobre una cuenta, el más viejo primero. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<MovementView> movementsOf(AccountId id) {
        throw new UnsupportedOperationException("paso 4");
    }

    /** HU06. La cadena de versiones de un movimiento, la original primero. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<RevisionView> revisionsOf(TransactionId id) {
        throw new UnsupportedOperationException("paso 4");
    }
}
