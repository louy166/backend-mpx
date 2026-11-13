package mr.foncier_api.dto.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import mr.foncier_api.entity.FormuleAbonnement;

import java.math.BigDecimal;

/**
 * Formule d'abonnement exposée sur GET /v1/plans (Contrat API §10).
 */
public record PlanDto(
        @JsonProperty("id") String id,
        @JsonProperty("nom") String nom,
        @JsonProperty("prix") BigDecimal prix,
        @JsonProperty("limite_annonces") int limiteAnnonces,
        @JsonProperty("deverrouillages_inclus") int deverrouillagesInclus
) {
    public static PlanDto fromEntity(FormuleAbonnement f) {
        return new PlanDto(
                f.getCode(),
                f.getNomFr(),
                normalizeAmount(f.getTarifAnnuel()),
                f.getLimiteAnnonces() != null ? f.getLimiteAnnonces() : 2,
                f.getDeverrouillagesInclus() != null ? f.getDeverrouillagesInclus() : 0
        );
    }

    public static BigDecimal normalizeAmount(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
