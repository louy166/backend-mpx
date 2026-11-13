package mr.foncier_api.repository;

import mr.foncier_api.entity.Deverrouillage;
import mr.foncier_api.enums.NiveauDeverrouillage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DeverrouillageRepository extends JpaRepository<Deverrouillage, UUID> {

    boolean existsByBienIdAndUtilisateurIdAndNiveau(
            UUID bienId,
            UUID utilisateurId,
            NiveauDeverrouillage niveau
    );

    @EntityGraph(attributePaths = {"bien", "bien.proprietaire"})
    Page<Deverrouillage> findByUtilisateurIdOrderByCreeLeDesc(UUID utilisateurId, Pageable pageable);
}
