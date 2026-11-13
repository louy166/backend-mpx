package mr.foncier_api.dto.mandat;

import com.fasterxml.jackson.annotation.JsonProperty;
import mr.foncier_api.dto.billing.PlanDto;
import mr.foncier_api.entity.Bien;
import mr.foncier_api.entity.Mandat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Représentation JSON d'un mandat (Contrat API §6).
 */
public record MandatDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("reference") String reference,
        @JsonProperty("bien_id") UUID bienId,
        @JsonProperty("bien_titre") String bienTitre,
        @JsonProperty("bien_ville") String bienVille,
        @JsonProperty("proprietaire_id") UUID proprietaireId,
        @JsonProperty("agent_id") UUID agentId,
        @JsonProperty("type") String type,
        @JsonProperty("status") String status,
        @JsonProperty("commission_pourcentage") BigDecimal commissionPourcentage,
        @JsonProperty("date_debut") LocalDate dateDebut,
        @JsonProperty("date_fin") LocalDate dateFin,
        @JsonProperty("conditions_particulieres") String conditionsParticulieres,
        @JsonProperty("date_creation") Instant dateCreation
) {
    public static MandatDto fromEntity(Mandat m) {
        Bien b = m.getBien();
        return new MandatDto(
                m.getId(),
                m.getReference(),
                b != null ? b.getId() : null,
                b != null ? b.getTitre() : null,
                b != null ? b.getVille() : null,
                m.getProprietaire() != null ? m.getProprietaire().getId() : null,
                m.getAgent() != null ? m.getAgent().getId() : null,
                m.getType() != null ? m.getType().name() : null,
                m.getStatut() != null ? m.getStatut().name() : null,
                PlanDto.normalizeAmount(m.getTauxCommission()),
                m.getDateDebut(),
                m.getDateFin(),
                m.getConditionsParticulieres(),
                m.getCreeLe()
        );
    }
}
