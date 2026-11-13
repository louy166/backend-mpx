package mr.foncier_api.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Requête de mise à jour partielle du profil (PUT /v1/users/me — Contrat API §3).
 */
public record UpdateProfileRequestDto(
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        @JsonProperty("nom")
        String nom,

        @Size(max = 100, message = "Le prénom ne doit pas dépasser 100 caractères")
        @JsonProperty("prenom")
        String prenom,

        @Email(message = "Format d'email invalide")
        @Size(max = 255, message = "L'email ne doit pas dépasser 255 caractères")
        @JsonProperty("email")
        String email,

        @Pattern(regexp = "^(fr|ar)$", message = "Langue invalide, valeurs attendues: fr, ar")
        @JsonProperty("langue")
        String langue
) {
}
