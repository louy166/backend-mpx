package mr.foncier_api.service;

import lombok.RequiredArgsConstructor;
import mr.foncier_api.dto.common.PageResponse;
import mr.foncier_api.dto.property.PropertySummaryDto;
import mr.foncier_api.entity.Bien;
import mr.foncier_api.entity.Favori;
import mr.foncier_api.entity.Utilisateur;
import mr.foncier_api.exception.BusinessException;
import mr.foncier_api.exception.ErrorCode;
import mr.foncier_api.repository.BienRepository;
import mr.foncier_api.repository.FavoriRepository;
import mr.foncier_api.repository.UtilisateurRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Service métier de gestion des Favoris utilisateur (Contrat API §4 : /v1/users/me/favorites).
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriRepository favoriRepository;
    private final BienRepository bienRepository;
    private final UtilisateurRepository utilisateurRepository;

    @Transactional
    public void addFavorite(UUID userId, UUID propertyId) {
        Utilisateur user = getActiveUserOrThrow(userId);
        Bien bien = bienRepository.findByIdAndEstArchiveFalse(propertyId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "Bien immobilier introuvable",
                        "العقار غير موجود"
                ));

        if (favoriRepository.existsByUtilisateurIdAndBienId(userId, propertyId)) {
            return;
        }

        Favori favori = Favori.builder()
                .utilisateur(user)
                .bien(bien)
                .build();
        favoriRepository.save(favori);
        bienRepository.incrementFavoris(propertyId);
    }

    @Transactional
    public void removeFavorite(UUID userId, UUID propertyId) {
        getActiveUserOrThrow(userId);
        Optional<Favori> existing = favoriRepository.findByUtilisateurIdAndBienId(userId, propertyId);
        if (existing.isPresent()) {
            favoriRepository.delete(existing.get());
            favoriRepository.flush();
            bienRepository.decrementFavoris(propertyId);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<PropertySummaryDto> listMyFavorites(UUID userId, int page, int perPage) {
        getActiveUserOrThrow(userId);
        int safePage = Math.max(1, page);
        int safePerPage = Math.min(100, Math.max(1, perPage));

        Page<Favori> favPage = favoriRepository.findByUtilisateurIdAndBienEstArchiveFalseOrderByCreeLeDesc(
                userId,
                PageRequest.of(safePage - 1, safePerPage)
        );

        Page<PropertySummaryDto> dtoPage = favPage.map(fav -> {
            Bien b = fav.getBien();
            boolean isOwner = b.getProprietaire() != null && userId.equals(b.getProprietaire().getId());
            return PropertySummaryDto.fromEntity(b, isOwner, true, null);
        });

        return PageResponse.from(dtoPage);
    }

    private Utilisateur getActiveUserOrThrow(UUID userId) {
        if (userId == null) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    "Token manquant ou expiré",
                    "الرمز مفقود أو منتهي الصلاحية"
            );
        }
        Utilisateur user = utilisateurRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.UNAUTHORIZED,
                        "Utilisateur introuvable",
                        "المستخدم غير موجود"
                ));
        if (!user.isEstActif()) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Ce compte a été suspendu",
                    "تم تعليق هذا الحساب"
            );
        }
        return user;
    }
}
