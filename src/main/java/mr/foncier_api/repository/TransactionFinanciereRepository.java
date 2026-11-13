package mr.foncier_api.repository;

import mr.foncier_api.entity.TransactionFinanciere;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TransactionFinanciereRepository extends JpaRepository<TransactionFinanciere, UUID> {

    @EntityGraph(attributePaths = {"bien"})
    Page<TransactionFinanciere> findByTiersIdAndEstArchiveFalseOrderByCreeLeDesc(UUID tiersId, Pageable pageable);

    @Query(value = "SELECT nextval('public.seq_transaction_reference')", nativeQuery = true)
    Long getNextReferenceSequenceValue();
}
