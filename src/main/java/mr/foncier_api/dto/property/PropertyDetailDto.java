package mr.foncier_api.dto.property;

import com.fasterxml.jackson.annotation.JsonProperty;
import mr.foncier_api.dto.common.GeoPointDto;
import mr.foncier_api.entity.Bien;
import mr.foncier_api.entity.Document;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Détail complet d'un bien retourné par GET /v1/properties/:id, POST /v1/properties et PUT /v1/properties/:id
 * (Contrat API §4 & §5).
 */
public record PropertyDetailDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("reference") String reference,
        @JsonProperty("titre") String titre,
        @JsonProperty("description") String description,
        @JsonProperty("description_ar") String descriptionAr,
        @JsonProperty("type") String type,
        @JsonProperty("status") String status,
        @JsonProperty("transaction") String transaction,
        @JsonProperty("prix") BigDecimal prix,
        @JsonProperty("devise") String devise,
        @JsonProperty("surface_m2") BigDecimal surfaceM2,
        @JsonProperty("nombre_pieces") Integer nombrePieces,
        @JsonProperty("nombre_chambres") Integer nombreChambres,
        @JsonProperty("nombre_salles_bain") Integer nombreSallesBain,
        @JsonProperty("adresse") String adresse,
        @JsonProperty("ville") String ville,
        @JsonProperty("quartier") String quartier,
        @JsonProperty("localisation") GeoPointDto localisation,
        @JsonProperty("photos") List<String> photos,
        @JsonProperty("proprietaire_id") UUID proprietaireId,
        @JsonProperty("proprietaire") PropertyOwnerDto proprietaire,
        @JsonProperty("documents") List<PropertyDocumentDto> documents,
        @JsonProperty("est_verifie") boolean estVerifie,
        @JsonProperty("est_vip") boolean estVip,
        @JsonProperty("nombre_vues") int nombreVues,
        @JsonProperty("est_favori") boolean estFavori,
        @JsonProperty("date_creation") Instant dateCreation,
        @JsonProperty("date_modification") Instant dateModification
) {
    public static PropertyDetailDto fromEntity(
            Bien bien,
            boolean exposeExactLocation,
            boolean estFavori,
            List<Document> docs,
            Function<Document, String> documentUrlResolver
    ) {
        Point pointToExpose = exposeExactLocation && bien.getPositionExacte() != null
                ? bien.getPositionExacte()
                : (bien.getPositionFloue() != null ? bien.getPositionFloue() : bien.getPositionExacte());

        List<String> photoList = bien.getPhotos() != null
                ? Arrays.asList(bien.getPhotos())
                : List.of();

        List<PropertyDocumentDto> docDtos = docs != null
                ? docs.stream()
                        .map(d -> PropertyDocumentDto.fromEntity(
                                d,
                                documentUrlResolver != null ? documentUrlResolver.apply(d) : null
                        ))
                        .toList()
                : List.of();

        return new PropertyDetailDto(
                bien.getId(),
                bien.getReference(),
                bien.getTitre(),
                bien.getDescriptionFr(),
                bien.getDescriptionAr(),
                bien.getType() != null ? bien.getType().name() : null,
                bien.getStatut() != null ? bien.getStatut().name() : null,
                bien.getTransaction() != null ? bien.getTransaction().name() : null,
                bien.getPrix(),
                bien.getDevise(),
                bien.getSuperficieM2(),
                bien.getNbPieces(),
                bien.getNbChambres(),
                bien.getNbSdb(),
                exposeExactLocation ? bien.getAdresse() : bien.getQuartierNom(),
                bien.getVille(),
                bien.getQuartierNom(),
                GeoPointDto.fromPoint(pointToExpose),
                photoList,
                bien.getProprietaire() != null ? bien.getProprietaire().getId() : null,
                PropertyOwnerDto.fromEntity(bien.getProprietaire()),
                docDtos,
                bien.isEstVerifie(),
                bien.isEstVip(),
                bien.getVues() != null ? bien.getVues() : 0,
                estFavori,
                bien.getCreeLe(),
                bien.getMajLe()
        );
    }
}
