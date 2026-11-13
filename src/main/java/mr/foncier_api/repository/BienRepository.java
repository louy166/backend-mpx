package mr.foncier_api.repository;

import mr.foncier_api.entity.Bien;
import mr.foncier_api.enums.StatutBien;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BienRepository extends JpaRepository<Bien, UUID>, BienRepositoryCustom {

    @EntityGraph(attributePaths = {"proprietaire"})
    Optional<Bien> findByIdAndEstArchiveFalse(UUID id);

    @EntityGraph(attributePaths = {"proprietaire"})
    Page<Bien> findByProprietaireIdAndEstArchiveFalseOrderByCreeLeDesc(UUID proprietaireId, Pageable pageable);

    @EntityGraph(attributePaths = {"proprietaire"})
    Page<Bien> findByProprietaireIdAndStatutAndEstArchiveFalseOrderByCreeLeDesc(
            UUID proprietaireId,
            StatutBien statut,
            Pageable pageable
    );

    long countByStatutAndEstArchiveFalse(StatutBien statut);

    long countByProprietaireIdAndEstArchiveFalse(UUID proprietaireId);

    @Query(value = "SELECT nextval('public.seq_bien_reference')", nativeQuery = true)
    Long getNextReferenceSequenceValue();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Bien b SET b.vues = b.vues + 1 WHERE b.id = :id")
    void incrementVues(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Bien b SET b.deverrouillages = b.deverrouillages + 1 WHERE b.id = :id")
    void incrementDeverrouillages(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Bien b SET b.favoris = b.favoris + 1 WHERE b.id = :id")
    void incrementFavoris(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Bien b SET b.favoris = CASE WHEN b.favoris > 0 THEN b.favoris - 1 ELSE 0 END WHERE b.id = :id")
    void decrementFavoris(@Param("id") UUID id);
}
