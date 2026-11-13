package mr.foncier_api.dto.mandat;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Requête de création d'un mandat sur POST /v1/mandats (Contrat API §6).
 */
public record CreateMandatRequestDto(
        @NotNull(message = "Le champ bien_id est requis")
        @JsonProperty("bien_id")
        UUID bienId,

        @JsonProperty("agent_id")
        UUID agentId,

        @NotBlank(message = "Le champ type est requis")
        @JsonProperty("type")
        String type,

        @NotNull(message = "Le champ commission_pourcentage est requis")
        @DecimalMin(value = "0.0", message = "Doit être supérieur ou égal à 0")
        @DecimalMax(value = "100.0", message = "Ne peut pas dépasser 100")
        @JsonProperty("commission_pourcentage")
        BigDecimal commissionPourcentage,

        @NotNull(message = "Le champ date_debut est requis")
        @JsonProperty("date_debut")
        LocalDate dateDebut,

        @NotNull(message = "Le champ date_fin est requis")
        @JsonProperty("date_fin")
        LocalDate dateFin,

        @JsonProperty("conditions_particulieres")
        String conditionsParticulieres
) {
}
