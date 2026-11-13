package mr.foncier_api.dto.property;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * Requête de modération / transition de statut d'un bien (PATCH /v1/properties/:id/status).
 */
public record UpdatePropertyStatusRequestDto(
        @NotBlank(message = "Le champ status est requis")
        @JsonProperty("status")
        String status,

        @JsonProperty("est_verifie")
        Boolean estVerifie,

        @JsonProperty("est_vip")
        Boolean estVip,

        @JsonProperty("prix_conclu")
        BigDecimal prixConclu,

        @JsonProperty("reserves")
        String reserves
) {
}
