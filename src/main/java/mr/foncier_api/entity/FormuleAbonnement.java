package mr.foncier_api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(name = "formule_abonnement", schema = "public")
public class FormuleAbonnement extends BaseEntity {

    @NotBlank
    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @NotBlank
    @Column(name = "nom_fr", nullable = false, length = 100)
    private String nomFr;

    @NotBlank
    @Column(name = "nom_ar", nullable = false, length = 100)
    private String nomAr;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "tarif_annuel", nullable = false, precision = 12, scale = 2)
    private BigDecimal tarifAnnuel;

    /**
     * Contrat API §10 : Limite d'annonces (-1 = illimité).
     */
    @NotNull
    @Builder.Default
    @Column(name = "limite_annonces", nullable = false)
    private Integer limiteAnnonces = 2;

    /**
     * Contrat API §10 : Déverrouillages inclus (-1 = illimité).
     */
    @NotNull
    @Builder.Default
    @Column(name = "deverrouillages_inclus", nullable = false)
    private Integer deverrouillagesInclus = 0;

    @NotNull
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fonctionnalites_incluses", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> fonctionnalitesIncluses = new HashMap<>();

    @Builder.Default
    @Column(name = "est_actif", nullable = false)
    private boolean estActif = true;
}
