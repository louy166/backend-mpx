package mr.foncier_api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import mr.foncier_api.entity.base.AuditableEntity;
import mr.foncier_api.enums.Langue;
import mr.foncier_api.enums.RoleUtilisateur;
import mr.foncier_api.enums.StatutKyc;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(
    name = "utilisateur",
    schema = "public",
    indexes = {
        @Index(name = "idx_utilisateur_telephone", columnList = "telephone", unique = true),
        @Index(name = "idx_utilisateur_email", columnList = "email"),
        @Index(name = "idx_utilisateur_role", columnList = "role")
    }
)
public class Utilisateur extends AuditableEntity {

    @NotBlank
    @Column(name = "telephone", nullable = false, unique = true, length = 30)
    private String telephone;

    @Column(name = "email", unique = true, length = 255)
    private String email;

    /**
     * Nullable pour permettre l'auto-création du compte au premier OTP vérifié (Contrat API §2, Checklist #12).
     */
    @Column(name = "mot_de_passe_hash", length = 255)
    private String motDePasseHash;

    @Column(name = "nom", length = 100)
    private String nom;

    @Column(name = "prenom", length = 100)
    private String prenom;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private RoleUtilisateur role = RoleUtilisateur.particulier;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "roles", columnDefinition = "text[]", nullable = false)
    private String[] roles = new String[]{"particulier"};

    @Column(name = "photo_url", columnDefinition = "text")
    private String photoUrl;

    @Column(name = "nni", length = 20)
    private String nni;

    @Builder.Default
    @Column(name = "kyc_verifie", nullable = false)
    private boolean kycVerifie = false;

    @Column(name = "kyc_recto_url", columnDefinition = "text")
    private String kycRectoUrl;

    @Column(name = "kyc_verso_url", columnDefinition = "text")
    private String kycVersoUrl;

    @Column(name = "kyc_selfie_url", columnDefinition = "text")
    private String kycSelfieUrl;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut_kyc", nullable = false, length = 20)
    private StatutKyc statutKyc = StatutKyc.non_verifie;

    @Column(name = "piece_identite_id")
    private UUID pieceIdentiteId;

    @NotBlank
    @Builder.Default
    @Column(name = "plan", nullable = false, length = 20)
    private String plan = "gratuit";

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "langue", nullable = false, length = 5)
    private Langue langue = Langue.fr;

    @Column(name = "pays_residence", length = 80)
    private String paysResidence;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "device_tokens", columnDefinition = "text[]", nullable = false)
    private String[] deviceTokens = new String[0];

    @Column(name = "score_qualification")
    private Integer scoreQualification;

    @NotNull
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "consentements", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> consentements = new HashMap<>();

    @Builder.Default
    @Column(name = "est_actif", nullable = false)
    private boolean estActif = true;

    @Column(name = "dernier_acces")
    private Instant dernierAcces;
}
