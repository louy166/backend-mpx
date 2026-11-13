package mr.foncier_api.entity;

import mr.foncier_api.enums.StatutMandat;
import mr.foncier_api.enums.TypeMandat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import mr.foncier_api.entity.base.AuditableEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(
    name = "mandat",
    schema = "public",
    indexes = {
        @Index(name = "idx_mandat_reference", columnList = "reference", unique = true),
        @Index(name = "idx_mandat_bien_id", columnList = "bien_id"),
        @Index(name = "idx_mandat_proprietaire_id", columnList = "proprietaire_id"),
        @Index(name = "idx_mandat_agent_id", columnList = "agent_id")
    }
)
public class Mandat extends AuditableEntity {

    @NotBlank
    @Column(name = "reference", nullable = false, unique = true, length = 30)
    private String reference;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TypeMandat type;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bien_id", nullable = false)
    private Bien bien;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proprietaire_id", nullable = false)
    private Utilisateur proprietaire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Utilisateur agent;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "taux_commission", nullable = false, precision = 5, scale = 2)
    private BigDecimal tauxCommission;

    @Column(name = "montant_forfaitaire", precision = 12, scale = 2)
    private BigDecimal montantForfaitaire;

    @NotNull
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @NotNull
    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    private StatutMandat statut = StatutMandat.brouillon;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "signature", columnDefinition = "jsonb")
    private Map<String, Object> signature;

    @Column(name = "conditions_particulieres", columnDefinition = "text")
    private String conditionsParticulieres;
}
