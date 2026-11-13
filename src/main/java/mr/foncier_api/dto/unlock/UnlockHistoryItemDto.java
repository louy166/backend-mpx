package mr.foncier_api.dto.unlock;

import com.fasterxml.jackson.annotation.JsonProperty;
import mr.foncier_api.dto.billing.PlanDto;
import mr.foncier_api.entity.Bien;
import mr.foncier_api.entity.Deverrouillage;
import mr.foncier_api.entity.Utilisateur;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Élément d'historique retourné par GET /v1/users/me/unlocks (Contrat API §8).
 */
public record UnlockHistoryItemDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("bien_id") UUID bienId,
        @JsonProperty("bien_titre") String bienTitre,
        @JsonProperty("bien_ville") String bienVille,
        @JsonProperty("bien_quartier") String bienQuartier,
        @JsonProperty("telephone_proprietaire") String telephoneProprietaire,
        @JsonProperty("email_proprietaire") String emailProprietaire,
        @JsonProperty("prix_paye") BigDecimal prixPaye,
        @JsonProperty("date_deverrouillage") Instant dateDeverrouillage
) {
    public static UnlockHistoryItemDto fromEntity(Deverrouillage d) {
        Bien b = d.getBien();
        Utilisateur proprio = b != null ? b.getProprietaire() : null;
        return new UnlockHistoryItemDto(
                d.getId(),
                b != null ? b.getId() : null,
                b != null ? b.getTitre() : null,
                b != null ? b.getVille() : null,
                b != null ? b.getQuartierNom() : null,
                proprio != null ? proprio.getTelephone() : null,
                proprio != null ? proprio.getEmail() : null,
                PlanDto.normalizeAmount(d.getMontantPaye()),
                d.getCreeLe()
        );
    }
}
