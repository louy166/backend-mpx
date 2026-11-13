package mr.foncier_api.entity;

import mr.foncier_api.enums.StatutAbonnement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import mr.foncier_api.entity.base.BaseEntity;

import java.time.LocalDate;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(
    name = "abonnement",
    schema = "public",
    indexes = {
        @Index(name = "idx_abonnement_utilisateur_statut", columnList = "utilisateur_id, statut")
    }
)
public class Abonnement extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "formule_id", nullable = false)
    private FormuleAbonnement formule;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    private StatutAbonnement statut = StatutAbonnement.en_attente_paiement;

    @NotNull
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @NotNull
    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    /**
     * Contrat API §10 : mobile_money (Sedad/Masrvi/Bankily) ou carte_bancaire.
     */
    @Column(name = "methode_paiement", length = 30)
    private String methodePaiement;

    @Column(name = "numero_paiement", length = 30)
    private String numeroPaiement;

    @Builder.Default
    @Column(name = "deverrouillages_utilises", nullable = false)
    private Integer deverrouillagesUtilises = 0;

    /**
     * EX-183 : Aucune reconduction tacite sans consentement explicite recueilli et horodaté à la souscription.
     */
    @Builder.Default
    @Column(name = "reconduction_tacite_acceptee", nullable = false)
    private boolean reconductionTaciteAcceptee = false;
}
