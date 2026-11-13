package mr.foncier_api.repository;

import mr.foncier_api.entity.Favori;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface FavoriRepository extends JpaRepository<Favori, UUID> {

    boolean existsByUtilisateurIdAndBienId(UUID utilisateurId, UUID bienId);

    Optional<Favori> findByUtilisateurIdAndBienId(UUID utilisateurId, UUID bienId);

    @Query("SELECT f.bien.id FROM Favori f WHERE f.utilisateur.id = :userId AND f.bien.id IN :bienIds")
    Set<UUID> findFavoriteBienIds(@Param("userId") UUID userId, @Param("bienIds") Collection<UUID> bienIds);

    @EntityGraph(attributePaths = {"bien", "bien.proprietaire"})
    Page<Favori> findByUtilisateurIdAndBienEstArchiveFalseOrderByCreeLeDesc(UUID utilisateurId, Pageable pageable);
}
