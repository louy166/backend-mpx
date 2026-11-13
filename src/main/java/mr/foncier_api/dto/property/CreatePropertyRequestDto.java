package mr.foncier_api.dto.property;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mr.foncier_api.dto.common.GeoPointDto;

import java.math.BigDecimal;

/**
 * Requête de création d'un bien immobilier (POST /v1/properties — Contrat API §4).
 */
public record CreatePropertyRequestDto(
        @NotBlank(message = "Le champ titre est requis")
        @Size(max = 255, message = "Le titre ne doit pas dépasser 255 caractères")
        @JsonProperty("titre")
        String titre,

        @NotBlank(message = "Le champ description est requis")
        @JsonProperty("description")
        String description,

        @JsonProperty("description_ar")
        String descriptionAr,

        @NotBlank(message = "Le champ type est requis")
        @JsonProperty("type")
        String type,

        @NotBlank(message = "Le champ transaction est requis")
        @JsonProperty("transaction")
        String transaction,

        @NotNull(message = "Le champ prix est requis")
        @DecimalMin(value = "0.01", message = "Doit être supérieur à 0")
        @JsonProperty("prix")
        BigDecimal prix,

        @Size(max = 3)
        @JsonProperty("devise")
        String devise,

        @DecimalMin(value = "0.01", message = "La surface doit être supérieure à 0")
        @JsonProperty("surface_m2")
        BigDecimal surfaceM2,

        @Min(value = 0, message = "Le nombre de pièces ne peut pas être négatif")
        @JsonProperty("nombre_pieces")
        Integer nombrePieces,

        @Min(value = 0, message = "Le nombre de chambres ne peut pas être négatif")
        @JsonProperty("nombre_chambres")
        Integer nombreChambres,

        @Min(value = 0, message = "Le nombre de salles de bain ne peut pas être négatif")
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
