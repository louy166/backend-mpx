package mr.foncier_api.dto.property;

import com.fasterxml.jackson.annotation.JsonProperty;
import mr.foncier_api.entity.Utilisateur;

import java.util.UUID;

/**
 * Résumé public du propriétaire inclus dans GET /v1/properties/:id (Contrat API §4).
 * Ne divulgue jamais le téléphone ni l'email avant déverrouillage payant (Contrat API §8, EX-120).
 */
public record PropertyOwnerDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("nom") String nom,
        @JsonProperty("prenom") String prenom,
        @JsonProperty("photo_url") String photoUrl
) {
    public static PropertyOwnerDto fromEntity(Utilisateur u) {
        if (u == null) {
            return null;
        }
        return new PropertyOwnerDto(
                u.getId(),
                u.getNom(),
                u.getPrenom(),
                u.getPhotoUrl()
        );
    }
}
