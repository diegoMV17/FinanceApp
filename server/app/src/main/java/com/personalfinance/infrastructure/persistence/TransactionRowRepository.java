package com.personalfinance.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TransactionRowRepository extends JpaRepository<TransactionRow, UUID> {

    /**
     * En el orden en que se registraron. El id desempata: dos movimientos
     * guardados en el mismo instante tienen que cargarse siempre igual, o el
     * historial cambiaría de orden entre arranques.
     */
    List<TransactionRow> findAllByOrderByRecordedAtAscIdAsc();
}
