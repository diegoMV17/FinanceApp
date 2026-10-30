package com.personalfinance.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Consultas derivadas del nombre del método. Ninguna cadena de SQL que armar,
 * así que no hay por dónde colar una inyección.
 */
interface AccountRowRepository extends JpaRepository<AccountRow, UUID> {

    List<AccountRow> findAllByOrderByCreatedAtAscIdAsc();
}
