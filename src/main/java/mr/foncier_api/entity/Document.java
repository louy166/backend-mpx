package mr.foncier_api.entity;

import mr.foncier_api.enums.CategorieDocument;
import mr.foncier_api.enums.NiveauConfidentialite;
import mr.foncier_api.enums.StatutVerificationDocument;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(
    name = "document",
    schema = "public",
    indexes = {
        @Index(name = "idx_document_bien_id", columnList = "bien_id"),
        @Index(name = "idx_document_utilisateur_id", columnList = "utilisateur_id"),
        @Index(name = "idx_document_mandat_id", columnList = "mandat_id")
    }
)
public class Document extends AuditableEntity {

    @Column(name = "bien_id")
    private UUID bienId;

    @Column(name = "utilisateur_id")
    private UUID utilisateurId;

    @Column(name = "mandat_id")
    private UUID mandatId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "categorie", nullable = false, length = 40)
    private CategorieDocument categorie;

    @NotBlank
    @Column(name = "nom_fichier", nullable = false, length = 255)
    private String nomFichier;

    @NotBlank
    @Column(name = "mime", nullable = false, length = 100)
    private String mime;

    @NotNull
    @Min(1)
    @Column(name = "taille", nullable = false)
    private Long taille;

    @NotBlank
    @Column(name = "cle_stockage", nullable = false, length = 500)
    private String cleStockage;

    @NotBlank
    @Column(name = "empreinte_sha256", nullable = false, length = 64)
    private String empreinteSha256;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_verification", nullable = false, length = 30)
    private StatutVerificationDocument statutVerification = StatutVerificationDocument.depose;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "donnees_extraites", columnDefinition = "jsonb")
    private Map<String, Object> donneesExtraites;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "confidentialite", nullable = false, length = 20)
    private NiveauConfidentialite confidentialite = NiveauConfidentialite.RESTREINT;

    @NotNull
    @Column(name = "depose_par", nullable = false)
    private UUID deposePar;

    @NotNull
    @Builder.Default
    @Column(name = "depose_le", nullable = false, updatable = false)
    private Instant deposeLe = Instant.now();

    @NotNull
    @Builder.Default
    @Min(1)
    @Column(name = "duree_conservation", nullable = false)
    private Integer dureeConservation = 36;

    @PrePersist
    @PreUpdate
    private void verifierRattachement() {
        if (bienId == null && utilisateurId == null && mandatId == null) {
            throw new IllegalStateException("Un document doit être rattaché à au moins un bien, un utilisateur ou un mandat.");
        }
    }
}
