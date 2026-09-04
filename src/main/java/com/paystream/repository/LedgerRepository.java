package com.paystream.repository;

import com.paystream.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Repository interface for LedgerEntry.
 */
@Repository
public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByAccountNumberOrderByCreatedAtDesc(String accountNumber);
}
