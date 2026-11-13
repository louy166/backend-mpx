package mr.foncier_api.dto.property;

import com.fasterxml.jackson.annotation.JsonProperty;
import mr.foncier_api.entity.Document;
import mr.foncier_api.enums.StatutVerificationDocument;

import java.time.Instant;
import java.util.UUID;

/**
 * Métadonnées d'un document rattaché à un bien dans POST /v1/properties/:id/documents et GET /v1/properties/:id
 * (Contrat API §4 & §5).
 */
public record PropertyDocumentDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("bien_id") UUID bienId,
        @JsonProperty("type") String type,
        @JsonProperty("nom_fichier") String nomFichier,
        @JsonProperty("url") String url,
        @JsonProperty("taille_octets") Long tailleOctets,
        @JsonProperty("date_upload") Instant dateUpload,
        @JsonProperty("verifie") boolean verifie
) {
    public static PropertyDocumentDto fromEntity(Document doc, String resolvedUrl) {
        return new PropertyDocumentDto(
                doc.getId(),
                doc.getBienId(),
                doc.getCategorie() != null ? doc.getCategorie().name() : "autre",
                doc.getNomFichier(),
                resolvedUrl,
                doc.getTaille(),
                doc.getDeposeLe(),
                doc.getStatutVerification() == StatutVerificationDocument.valide
        );
    }
}
