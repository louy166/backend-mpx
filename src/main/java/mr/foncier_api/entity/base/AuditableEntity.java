package mr.foncier_api.entity.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Socle pour les entités soumises à l'interdiction de suppression physique (EX-035 MUST).
 * Toute suppression passe par un archivage logique.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@MappedSuperclass
public abstract class AuditableEntity extends BaseEntity {

    @Builder.Default
    @Column(name = "est_archive", nullable = false)
    private boolean estArchive = false;

    @Column(name = "archive_le")
    private Instant archiveLe;
}
