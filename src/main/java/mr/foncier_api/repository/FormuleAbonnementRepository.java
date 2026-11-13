package mr.foncier_api.repository;

import mr.foncier_api.entity.FormuleAbonnement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FormuleAbonnementRepository extends JpaRepository<FormuleAbonnement, UUID> {

    Optional<FormuleAbonnement> findByCodeIgnoreCase(String code);

    List<FormuleAbonnement> findByEstActifTrueOrderByTarifAnnuelAsc();
}
