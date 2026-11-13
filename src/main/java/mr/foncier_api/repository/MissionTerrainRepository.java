package mr.foncier_api.repository;

import mr.foncier_api.entity.MissionTerrain;
import mr.foncier_api.enums.StatutMissionTerrain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MissionTerrainRepository extends JpaRepository<MissionTerrain, UUID> {

    @Override
    @EntityGraph(attributePaths = {"bien", "bien.proprietaire", "agent"})
    Optional<MissionTerrain> findById(UUID id);

    @EntityGraph(attributePaths = {"bien", "bien.proprietaire", "agent"})
    Page<MissionTerrain> findByAgentIdOrderByDatePlanifieeDesc(UUID agentId, Pageable pageable);

    @EntityGraph(attributePaths = {"bien", "bien.proprietaire", "agent"})
    Page<MissionTerrain> findByAgentIdAndStatutOrderByDatePlanifieeDesc(
            UUID agentId,
            StatutMissionTerrain statut,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"bien", "bien.proprietaire", "agent"})
    Page<MissionTerrain> findAllByOrderByDatePlanifieeDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"bien", "bien.proprietaire", "agent"})
    Page<MissionTerrain> findByStatutOrderByDatePlanifieeDesc(StatutMissionTerrain statut, Pageable pageable);

    boolean existsByBienIdAndAgentId(UUID bienId, UUID agentId);

    @Query(value = "SELECT nextval('public.seq_mission_reference')", nativeQuery = true)
    Long getNextReferenceSequenceValue();
}
