package mr.foncier_api.dto.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import mr.foncier_api.entity.Abonnement;
import mr.foncier_api.entity.FormuleAbonnement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Détail de l'abonnement actif retourné par POST /v1/subscriptions et GET /v1/users/me/subscription (Contrat API §10).
 */
public record SubscriptionDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("plan_id") String planId,
        @JsonProperty("plan_nom") String planNom,
        @JsonProperty("statut") String statut,
        @JsonProperty("prix") BigDecimal prix,
        @JsonProperty("limite_annonces") int limiteAnnonces,
        @JsonProperty("deverrouillages_inclus") int deverrouillagesInclus,
        @JsonProperty("deverrouillages_utilises") int deverrouillagesUtilises,
        @JsonProperty("deverrouillages_restants") int deverrouillagesRestants,
        @JsonProperty("methode_paiement") String methodePaiement,
        @JsonProperty("numero_paiement") String numeroPaiement,
        @JsonProperty("date_debut") LocalDate dateDebut,
        @JsonProperty("date_fin") LocalDate dateFin
) {
    public static SubscriptionDto fromEntity(Abonnement a) {
        FormuleAbonnement f = a.getFormule();
        int inclus = f.getDeverrouillagesInclus() != null ? f.getDeverrouillagesInclus() : 0;
        int utilises = a.getDeverrouillagesUtilises() != null ? a.getDeverrouillagesUtilises() : 0;
        int restants = inclus == -1 ? -1 : Math.max(0, inclus - utilises);

        return new SubscriptionDto(
                a.getId(),
                f.getCode(),
                f.getNomFr(),
                a.getStatut().name(),
                PlanDto.normalizeAmount(f.getTarifAnnuel()),
                f.getLimiteAnnonces() != null ? f.getLimiteAnnonces() : 2,
                inclus,
                utilises,
                restants,
                a.getMethodePaiement(),
                a.getNumeroPaiement(),
                a.getDateDebut(),
                a.getDateFin()
        );
    }

    public static SubscriptionDto defaultFromPlan(FormuleAbonnement f) {
        int inclus = f.getDeverrouillagesInclus() != null ? f.getDeverrouillagesInclus() : 0;
        return new SubscriptionDto(
                null,
                f.getCode(),
                f.getNomFr(),
                "actif",
                PlanDto.normalizeAmount(f.getTarifAnnuel()),
                f.getLimiteAnnonces() != null ? f.getLimiteAnnonces() : 2,
                inclus,
                0,
                inclus,
                null,
                null,
                null,
                null
        );
    }
}
