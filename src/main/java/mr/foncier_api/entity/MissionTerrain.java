package mr.foncier_api.entity;

import mr.foncier_api.enums.StatutMissionTerrain;
import mr.foncier_api.enums.TypeMissionTerrain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(
    name = "mission_terrain",
    schema = "public",
    indexes = {
        @Index(name = "idx_mission_reference", columnList = "reference", unique = true),
        @Index(name = "idx_mission_agent_statut", columnList = "agent_id, statut"),
        @Index(name = "idx_mission_bien_id", columnList = "bien_id")
    }
)
public class MissionTerrain extends BaseEntity {

    @NotBlank
    @Column(name = "reference", nullable = false, unique = true, length = 30)
    private String reference;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bien_id", nullable = false)
    private Bien bien;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private Utilisateur agent;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TypeMissionTerrain type;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutMissionTerrain statut = StatutMissionTerrain.assignee;

    @NotNull
    @Column(name = "date_planifiee", nullable = false)
    private Instant datePlanifiee;

    @Column(name = "date_realisee")
    private Instant dateRealisee;

    @Column(name = "releve_gps", columnDefinition = "geography(Point,4326)")
    private Point releveGps;

    /**
     * Précision du relevé GPS en mètres — une valeur > 15 m impose une reprise (M8).
     */
    @Column(name = "precision_m", precision = 6, scale = 2)
    private BigDecimal precisionM;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "photos", columnDefinition = "text[]", nullable = false)
    private String[] photos = new String[0];

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "medias", columnDefinition = "jsonb")
    private List<Map<String, Object>> medias = new ArrayList<>();

    /**
     * Nullable lors de l'assignation initiale de la mission, renseigné lors de l'exécution (Contrat API §7).
     */
    @Column(name = "compte_rendu", columnDefinition = "text")
    private String compteRendu;

    @NotNull
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checklist", columnDefinition = "jsonb", nullable = false)
    private Map<String, Boolean> checklist = new HashMap<>();
}
