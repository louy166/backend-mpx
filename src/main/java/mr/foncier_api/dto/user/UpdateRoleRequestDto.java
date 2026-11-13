package mr.foncier_api.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Requête de changement de rôle (PUT /v1/users/me/role — Contrat API §3).
 */
public record UpdateRoleRequestDto(
        @NotBlank(message = "Le champ role est requis")
        @JsonProperty("role")
        String role
) {
}
