package mr.foncier_api.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Réponse d'upload de photo de profil (POST /v1/users/me/photo — Contrat API §3).
 */
public record PhotoUploadResponseDto(
        @JsonProperty("photo_url")
        String photoUrl
) {
}
