package mr.foncier_api.dto.mission;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import mr.foncier_api.dto.common.GeoPointDto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Requête de mise à jour d'une mission terrain sur PATCH /v1/missions/:id (Contrat API §7).
 */
public record UpdateMissionRequestDto(
        @JsonProperty("status")
        String status,

        @JsonProperty("rapport")
        String rapport,

        @Valid
        @JsonProperty("localisation_visite")
        GeoPointDto localisationVisite,

        @JsonProperty("precision_m")
        BigDecimal precisionM,

        @JsonProperty("checklist")
        Map<String, Boolean> checklist
) {
}
