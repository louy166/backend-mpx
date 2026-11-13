package mr.foncier_api.entity;

import mr.foncier_api.enums.SensTransaction;
import mr.foncier_api.enums.StatutTransactionFinanciere;
import mr.foncier_api.enums.TypeTransactionFinanciere;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import mr.foncier_api.entity.base.AuditableEntity;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(
    name = "transaction_financiere",
    schema = "public",
    indexes = {
        @Index(name = "idx_transaction_reference", columnList = "reference", unique = true),
        @Index(name = "idx_transaction_tiers_statut", columnList = "tiers_id, statut"),
        @Index(name = "idx_transaction_bien_id", columnList = "bien_id")
    }
)
public class TransactionFinanciere extends AuditableEntity {

    @NotBlank
    @Column(name = "reference", nullable = false, unique = true, length = 30)
    private String reference;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private TypeTransactionFinanciere type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "sens", nullable = false, length = 20)
    private SensTransaction sens;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "montant_ht", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantHt;

    @NotNull
    @Builder.Default
    @DecimalMin("0.00")
    @Column(name = "taxe", nullable = false, precision = 15, scale = 2)
    private BigDecimal taxe = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.00")
    @Column(name = "montant_ttc", nullable = false, precision = 15, scale = 2)
    private BigDecimal montantTtc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tiers_id")
    private Utilisateur tiers;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bien_id")
    private Bien bien;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mandat_id")
    private Mandat mandat;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    private StatutTransactionFinanciere statut = StatutTransactionFinanciere.a_facturer;

    @Column(name = "piece_id")
    private UUID pieceId;

    /**
     * EX-241 : Aucune facture ne peut être supprimée ; l'annulation passe obligatoirement par un avoir.
     */
    @Column(name = "transaction_avoir_id")
    private UUID transactionAvoirId;
}
