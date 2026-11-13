package mr.foncier_api.dto.mission;

import com.fasterxml.jackson.annotation.JsonProperty;
import mr.foncier_api.dto.common.GeoPointDto;
import mr.foncier_api.dto.property.PropertyDetailDto;
import mr.foncier_api.entity.Document;
import mr.foncier_api.entity.MissionTerrain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Détail complet d'une mission terrain avec son bien immobilier associé (Contrat API §7).
 */
public record MissionDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("reference") String reference,
        @JsonProperty("type") String type,
        @JsonProperty("status") String status,
        @JsonProperty("agent_id") UUID agentId,
        @JsonProperty("bien_id") UUID bienId,
        @JsonProperty("bien") PropertyDetailDto bien,
        @JsonProperty("rapport") String rapport,
        @JsonProperty("localisation_visite") GeoPointDto localisationVisite,
        @JsonProperty("precision_m") BigDecimal precisionM,
        @JsonProperty("photos") List<String> photos,
        @JsonProperty("checklist") Map<String, Boolean> checklist,
        @JsonProperty("date_planifiee") Instant datePlanifiee,
        @JsonProperty("date_realisee") Instant dateRealisee,
        @JsonProperty("date_creation") Instant dateCreation
) {
    public static MissionDto fromEntity(
            MissionTerrain m,
            List<Document> bienDocs,
            Function<Document, String> documentUrlResolver
    ) {
        List<String> photoList = m.getPhotos() != null
                ? Arrays.asList(m.getPhotos())
                : List.of();

        PropertyDetailDto bienDto = m.getBien() != null
                ? PropertyDetailDto.fromEntity(m.getBien(), true, false, bienDocs, documentUrlResolver)
                : null;

        return new MissionDto(
                m.getId(),
                m.getReference(),
                m.getType() != null ? m.getType().name() : null,
                m.getStatut() != null ? m.getStatut().name() : null,
                m.getAgent() != null ? m.getAgent().getId() : null,
                m.getBien() != null ? m.getBien().getId() : null,
                bienDto,
                m.getCompteRendu(),
                GeoPointDto.fromPoint(m.getReleveGps()),
                m.getPrecisionM(),
                photoList,
                m.getChecklist() != null ? m.getChecklist() : Map.of(),
                m.getDatePlanifiee(),
                m.getDateRealisee(),
                m.getCreeLe()
        );
    }
}
