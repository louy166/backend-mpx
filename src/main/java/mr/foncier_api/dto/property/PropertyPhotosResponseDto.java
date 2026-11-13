package mr.foncier_api.dto.property;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Réponse d'upload de photos sur un bien (POST /v1/properties/:id/photos — Contrat API §5).
 */
public record PropertyPhotosResponseDto(
        @JsonProperty("photos") List<String> photos
) {
}
