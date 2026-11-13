package mr.foncier_api.dto.mandat;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Requête de changement de statut d'un mandat sur PATCH /v1/mandats/:id/status (Contrat API §6 & §11).
 */
public record UpdateMandatStatusRequestDto(
        @NotBlank(message = "Le champ status est requis")
        @JsonProperty("status")
        String status
) {
}
