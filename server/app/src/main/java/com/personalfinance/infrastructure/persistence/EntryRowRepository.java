package com.personalfinance.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EntryRowRepository extends JpaRepository<EntryRow, UUID> {

    List<EntryRow> findAllByTransactionId(UUID transactionId);

    List<EntryRow> findAllByTransactionIdIn(List<UUID> transactionIds);
}
