package mr.foncier_api.repository;

import mr.foncier_api.entity.Mandat;
import mr.foncier_api.enums.StatutMandat;
import mr.foncier_api.enums.TypeMandat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MandatRepository extends JpaRepository<Mandat, UUID> {

    @EntityGraph(attributePaths = {"bien", "proprietaire", "agent"})
    Optional<Mandat> findByIdAndEstArchiveFalse(UUID id);

    @EntityGraph(attributePaths = {"bien", "proprietaire", "agent"})
    Page<Mandat> findByProprietaireIdAndEstArchiveFalseOrderByCreeLeDesc(UUID proprietaireId, Pageable pageable);

    @EntityGraph(attributePaths = {"bien", "proprietaire", "agent"})
    Page<Mandat> findByProprietaireIdAndStatutAndEstArchiveFalseOrderByCreeLeDesc(
            UUID proprietaireId,
            StatutMandat statut,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"bien", "proprietaire", "agent"})
    Page<Mandat> findByAgentIdAndEstArchiveFalseOrderByCreeLeDesc(UUID agentId, Pageable pageable);

    @EntityGraph(attributePaths = {"bien", "proprietaire", "agent"})
    Page<Mandat> findByAgentIdAndStatutAndEstArchiveFalseOrderByCreeLeDesc(
            UUID agentId,
            StatutMandat statut,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"bien", "proprietaire", "agent"})
    Page<Mandat> findByEstArchiveFalseOrderByCreeLeDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"bien", "proprietaire", "agent"})
    Page<Mandat> findByStatutAndEstArchiveFalseOrderByCreeLeDesc(StatutMandat statut, Pageable pageable);

    boolean existsByBienIdAndStatutAndEstArchiveFalse(UUID bienId, StatutMandat statut);

    boolean existsByBienIdAndStatutAndTypeInAndEstArchiveFalse(
            UUID bienId,
            StatutMandat statut,
            Collection<TypeMandat> types
    );

    @Query(value = "SELECT nextval('public.seq_mandat_reference')", nativeQuery = true)
    Long getNextReferenceSequenceValue();
}
