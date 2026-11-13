package mr.foncier_api.dto.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Requête de souscription à un plan sur POST /v1/subscriptions (Contrat API §10).
 */
public record CreateSubscriptionRequestDto(
        @NotBlank(message = "Le champ plan_id est requis")
        @JsonProperty("plan_id")
        String planId,

        @JsonProperty("methode_paiement")
        String methodePaiement,

        @JsonProperty("numero_paiement")
        String numeroPaiement
) {
}
