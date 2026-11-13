package mr.foncier_api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import mr.foncier_api.entity.base.AuditableEntity;
import mr.foncier_api.enums.NiveauVerification;
import mr.foncier_api.enums.StatutBien;
import mr.foncier_api.enums.TypeBien;
import mr.foncier_api.enums.TypeTransactionBien;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(
    name = "bien",
    schema = "public",
    indexes = {
        @Index(name = "idx_bien_reference", columnList = "reference", unique = true),
        @Index(name = "idx_bien_identifiant_parcellaire", columnList = "identifiant_parcellaire_national"),
        @Index(name = "idx_bien_statut_type_prix", columnList = "statut, type, prix"),
        @Index(name = "idx_bien_ville", columnList = "ville"),
        @Index(name = "idx_bien_proprietaire_id", columnList = "proprietaire_id")
    }
)
public class Bien extends AuditableEntity {

    @NotBlank
    @Column(name = "reference", nullable = false, unique = true, length = 30)
    private String reference;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private TypeBien type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction", nullable = false, length = 30)
    private TypeTransactionBien transaction;

    @NotBlank
    @Column(name = "titre", nullable = false, length = 255)
    private String titre;

    @NotBlank
    @Column(name = "description_fr", nullable = false, columnDefinition = "text")
    private String descriptionFr;

    /**
     * Peut être généré par IA puis relu (CDC 4.1) — nullable lors de la soumission initiale d'un brouillon.
     */
    @Column(name = "description_ar", columnDefinition = "text")
    private String descriptionAr;

    @DecimalMin("0.01")
    @Column(name = "superficie_m2", precision = 12, scale = 2)
    private BigDecimal superficieM2;

    @Column(name = "surface_batie_m2", precision = 12, scale = 2)
    private BigDecimal surfaceBatieM2;

    @Column(name = "nb_pieces")
    private Integer nbPieces;

    @Column(name = "nb_chambres")
    private Integer nbChambres;

    @Column(name = "nb_sdb")
    private Integer nbSdb;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "prix", nullable = false, precision = 15, scale = 2)
    private BigDecimal prix;

    @NotBlank
    @Builder.Default
    @Column(name = "devise", nullable = false, length = 3)
    private String devise = "MRU";

    @Builder.Default
    @Column(name = "prix_negociable", nullable = false)
    private boolean prixNegociable = false;

    @Column(name = "loyer_mensuel", precision = 12, scale = 2)
    private BigDecimal loyerMensuel;

    @Column(name = "charges_mensuelles", precision = 12, scale = 2)
    private BigDecimal chargesMensuelles;

    @Column(name = "adresse", columnDefinition = "text")
    private String adresse;

    @Column(name = "ville", length = 100)
    private String ville;

    @Column(name = "quartier_nom", length = 100)
    private String quartierNom;

    /**
     * Coordonnées GPS réelles — JAMAIS exposées avant le niveau N3 (EX-030, EX-120).
     */
    @Column(name = "position_exacte", columnDefinition = "geography(Point,4326)")
    private Point positionExacte;

    /**
     * Point décalé aléatoirement, recalculé à chaque publication (EX-031).
     */
    @Column(name = "position_floue", columnDefinition = "geography(Point,4326)")
    private Point positionFloue;

    @NotNull
    @Builder.Default
    @Min(50)
    @Column(name = "rayon_flou_m", nullable = false)
    private Integer rayonFlouM = 300;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "photos", columnDefinition = "text[]", nullable = false)
    private String[] photos = new String[0];

    /**
     * Champ réservé et indexé pour le recensement foncier national (EX-032).
     */
    @Column(name = "identifiant_parcellaire_national", length = 100)
    private String identifiantParcellaireNational;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    private StatutBien statut = StatutBien.brouillon;

    @Builder.Default
    @Column(name = "est_verifie", nullable = false)
    private boolean estVerifie = false;

    @Builder.Default
    @Column(name = "est_vip", nullable = false)
    private boolean estVip = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "niveau_verification", length = 5)
    private NiveauVerification niveauVerification;

    @Column(name = "reserves", columnDefinition = "text")
    private String reserves;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pieces_consultees", columnDefinition = "jsonb")
    private List<String> piecesConsultees;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proprietaire_id", nullable = false)
    private Utilisateur proprietaire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apporteur_id")
    private Utilisateur apporteur;

    @Column(name = "mandat_id")
    private UUID mandatId;

    @Column(name = "prix_conclu", precision = 15, scale = 2)
    private BigDecimal prixConclu;

    @Column(name = "date_conclusion")
    private LocalDate dateConclusion;

    @NotNull
    @Builder.Default
    @Min(0)
    @Column(name = "vues", nullable = false)
    private Integer vues = 0;

    @NotNull
    @Builder.Default
    @Min(0)
    @Column(name = "deverrouillages", nullable = false)
    private Integer deverrouillages = 0;

    @NotNull
    @Builder.Default
    @Min(0)
    @Column(name = "favoris", nullable = false)
    private Integer favoris = 0;

    @Column(name = "publie_le")
    private Instant publieLe;

    @Column(name = "expire_le")
    private Instant expireLe;

    @PrePersist
    @PreUpdate
    private void verifierReglesMetier() {
        if ((statut == StatutBien.vendu || statut == StatutBien.loue)
                && (prixConclu == null || prixConclu.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new IllegalStateException("EX-033 : Le passage à l'état vendu ou loué exige la saisie du prix réellement conclu.");
        }
    }
}
