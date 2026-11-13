package mr.foncier_api.dto.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import mr.foncier_api.entity.TransactionFinanciere;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Facture / transaction financière de l'utilisateur exposée sur GET /v1/users/me/invoices (Contrat API §10).
 */
public record InvoiceDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("reference") String reference,
        @JsonProperty("type") String type,
        @JsonProperty("montant") BigDecimal montant,
        @JsonProperty("devise") String devise,
        @JsonProperty("statut") String statut,
        @JsonProperty("bien_id") UUID bienId,
        @JsonProperty("date_creation") Instant dateCreation
) {
    public static InvoiceDto fromEntity(TransactionFinanciere tx) {
        return new InvoiceDto(
                tx.getId(),
                tx.getReference(),
                tx.getType() != null ? tx.getType().name() : null,
                PlanDto.normalizeAmount(tx.getMontantTtc()),
                "MRU",
                tx.getStatut() != null ? tx.getStatut().name() : null,
                tx.getBien() != null ? tx.getBien().getId() : null,
                tx.getCreeLe()
        );
    }
}
