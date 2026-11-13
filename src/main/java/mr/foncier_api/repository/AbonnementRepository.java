package mr.foncier_api.repository;

import mr.foncier_api.entity.Abonnement;
import mr.foncier_api.enums.StatutAbonnement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AbonnementRepository extends JpaRepository<Abonnement, UUID> {

    @EntityGraph(attributePaths = {"formule"})
    Optional<Abonnement> findFirstByUtilisateurIdAndStatutAndDateFinGreaterThanEqualOrderByDateDebutDesc(
            UUID utilisateurId,
            StatutAbonnement statut,
            LocalDate today
    );

    List<Abonnement> findByUtilisateurIdAndStatut(UUID utilisateurId, StatutAbonnement statut);
}
