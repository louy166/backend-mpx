package mr.foncier_api.dto.property;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import mr.foncier_api.dto.common.GeoPointDto;

import java.math.BigDecimal;

/**
 * Requête de modification partielle d'un bien immobilier (PUT /v1/properties/:id — Contrat API §4).
 */
public record UpdatePropertyRequestDto(
        @Size(max = 255, message = "Le titre ne doit pas dépasser 255 caractères")
        @JsonProperty("titre")
        String titre,

        @JsonProperty("description")
        String description,

        @JsonProperty("description_ar")
        String descriptionAr,

        @JsonProperty("type")
        String type,

        @JsonProperty("transaction")
        String transaction,

        @DecimalMin(value = "0.01", message = "Doit être supérieur à 0")
        @JsonProperty("prix")
        BigDecimal prix,

        @Size(max = 3)
        @JsonProperty("devise")
        String devise,

        @DecimalMin(value = "0.01", message = "La surface doit être supérieure à 0")
        @JsonProperty("surface_m2")
        BigDecimal surfaceM2,

        @Min(value = 0)
        @JsonProperty("nombre_pieces")
        Integer nombrePieces,

        @Min(value = 0)
        @JsonProperty("nombre_chambres")
        Integer nombreChambres,

        @Min(value = 0)
        @JsonProperty("nombre_salles_bain")
        Integer nombreSallesBain,

        @JsonProperty("adresse")
        String adresse,

        @Size(max = 100)
        @JsonProperty("ville")
        String ville,

        @Size(max = 100)
        @JsonProperty("quartier")
        String quartier,

        @JsonProperty("localisation")
        GeoPointDto localisation,

        @JsonProperty("status")
        String status
) {
}
