package mr.foncier_api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import mr.foncier_api.entity.base.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Notifications in-app (Contrat API §9 : GET /notifications, PATCH /notifications/:id/read, POST /notifications/read-all).
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(
    name = "notification",
    schema = "public",
    indexes = {
        @Index(name = "idx_notification_utilisateur_lu", columnList = "utilisateur_id, lu, cree_le")
    }
)
public class Notification extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @NotBlank
    @Column(name = "titre", nullable = false, length = 200)
    private String titre;

    @NotBlank
    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @NotBlank
    @Builder.Default
    @Column(name = "type", nullable = false, length = 50)
    private String type = "info";

    @Builder.Default
    @Column(name = "lu", nullable = false)
    private boolean lu = false;

    @Column(name = "lu_le")
    private Instant luLe;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata = new HashMap<>();
}
