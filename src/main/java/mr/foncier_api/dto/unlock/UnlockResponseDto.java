package mr.foncier_api.dto.unlock;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Réponse au déverrouillage des coordonnées d'un bien sur POST /v1/properties/:id/unlock (Contrat API §8).
 */
public record UnlockResponseDto(
        @JsonProperty("telephone_proprietaire") String telephoneProprietaire,
        @JsonProperty("email_proprietaire") String emailProprietaire,
        @JsonProperty("prix_paye") BigDecimal prixPaye
) {
}
