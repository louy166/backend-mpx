package mr.foncier_api.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import mr.foncier_api.entity.base.BaseEntity;

/**
 * Favoris utilisateur (Contrat API §4 : POST/DELETE/GET /users/me/favorites).
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(
    name = "favori",
    schema = "public",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_favori_utilisateur_bien", columnNames = {"utilisateur_id", "bien_id"})
    },
    indexes = {
        @Index(name = "idx_favori_utilisateur_cree", columnList = "utilisateur_id, cree_le")
    }
)
public class Favori extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bien_id", nullable = false)
    private Bien bien;
}
