package mr.foncier_api.dto.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import mr.foncier_api.entity.Notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Représentation JSON d'une notification in-app (Contrat API §9).
 */
public record NotificationDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("titre") String titre,
        @JsonProperty("message") String message,
        @JsonProperty("type") String type,
        @JsonProperty("lu") boolean lu,
        @JsonProperty("lu_le") Instant luLe,
        @JsonProperty("metadata") Map<String, Object> metadata,
        @JsonProperty("date_creation") Instant dateCreation
) {
    public static NotificationDto fromEntity(Notification n) {
        return new NotificationDto(
                n.getId(),
                n.getTitre(),
                n.getMessage(),
                n.getType(),
                n.isLu(),
                n.getLuLe(),
                n.getMetadata() != null ? n.getMetadata() : Map.of(),
                n.getCreeLe()
        );
    }
}
