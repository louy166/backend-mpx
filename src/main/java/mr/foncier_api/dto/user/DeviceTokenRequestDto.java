package mr.foncier_api.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Requête d'enregistrement de jeton push FCM/APNs (PUT /v1/users/me/device — Contrat API §9).
 */
public record DeviceTokenRequestDto(
        @NotBlank(message = "Le champ device_token est requis")
        @JsonProperty("device_token")
        String deviceToken
) {
}
