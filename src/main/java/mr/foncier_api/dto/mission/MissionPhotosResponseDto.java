package mr.foncier_api.dto.mission;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Réponse à l'upload de photos de vérification d'une mission terrain sur POST /v1/missions/:id/photos (Contrat API §7).
 */
public record MissionPhotosResponseDto(
        @JsonProperty("photos") List<String> photos
) {
}
