package mr.foncier_api.dto.mission;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Requête back-office (Admin / Vérificateur) de création et d'assignation d'une mission terrain sur POST /v1/missions.
 */
public record CreateMissionRequestDto(
        @NotNull(message = "Le champ bien_id est requis")
        @JsonProperty("bien_id")
        UUID bienId,

        @NotNull(message = "Le champ agent_id est requis")
        @JsonProperty("agent_id")
        UUID agentId,

        @JsonProperty("type")
        String type,

        @JsonProperty("date_planifiee")
        Instant datePlanifiee
) {
}
