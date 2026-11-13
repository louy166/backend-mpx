package mr.foncier_api.repository;

import mr.foncier_api.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUtilisateurIdOrderByCreeLeDesc(UUID utilisateurId, Pageable pageable);

    Page<Notification> findByUtilisateurIdAndLuOrderByCreeLeDesc(UUID utilisateurId, boolean lu, Pageable pageable);

    Optional<Notification> findByIdAndUtilisateurId(UUID id, UUID utilisateurId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.lu = true, n.luLe = :now WHERE n.utilisateur.id = :userId AND n.lu = false")
    int markAllAsRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
