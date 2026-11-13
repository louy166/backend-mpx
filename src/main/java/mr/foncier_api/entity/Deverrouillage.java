package mr.foncier_api.entity;

import mr.foncier_api.enums.MotifDeverrouillage;
import mr.foncier_api.enums.NiveauDeverrouillage;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import mr.foncier_api.entity.base.BaseEntity;

import java.math.BigDecimal;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(
    name = "deverrouillage",
    schema = "public",
    indexes = {
        @Index(name = "idx_deverrouillage_bien_utilisateur", columnList = "bien_id, utilisateur_id"),
        @Index(name = "idx_deverrouillage_utilisateur_cree", columnList = "utilisateur_id, cree_le")
    }
)
public class Deverrouillage extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bien_id", nullable = false)
    private Bien bien;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "niveau", nullable = false, length = 10)
    private NiveauDeverrouillage niveau;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "motif", nullable = false, length = 30)
    private MotifDeverrouillage motif;

    @Column(name = "montant_paye", precision = 10, scale = 2)
    private BigDecimal montantPaye;

    @NotBlank
    @Column(name = "adresse_ip", nullable = false, length = 45)
    private String adresseIp;

    @NotBlank
    @Column(name = "empreinte_appareil", nullable = false, length = 255)
    private String empreinteAppareil;

    @NotBlank
    @Column(name = "agent_utilisateur", nullable = false, columnDefinition = "text")
    private String agentUtilisateur;

    @Column(name = "engagement_signe", nullable = false)
    private boolean engagementSigne;
}
