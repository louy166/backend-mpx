package mr.foncier_api.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import mr.foncier_api.dto.billing.PlanDto;
import mr.foncier_api.dto.common.PageResponse;
import mr.foncier_api.dto.unlock.UnlockHistoryItemDto;
import mr.foncier_api.dto.unlock.UnlockResponseDto;
import mr.foncier_api.entity.Abonnement;
import mr.foncier_api.entity.Bien;
import mr.foncier_api.entity.Deverrouillage;
import mr.foncier_api.entity.Utilisateur;
import mr.foncier_api.enums.MotifDeverrouillage;
import mr.foncier_api.enums.NiveauDeverrouillage;
import mr.foncier_api.enums.StatutAbonnement;
import mr.foncier_api.enums.StatutBien;
import mr.foncier_api.enums.TypeTransactionFinanciere;
import mr.foncier_api.exception.BusinessException;
import mr.foncier_api.exception.ErrorCode;
import mr.foncier_api.repository.AbonnementRepository;
import mr.foncier_api.repository.BienRepository;
import mr.foncier_api.repository.DeverrouillageRepository;
import mr.foncier_api.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service de déverrouillage payant ou sur quota d'abonnement des coordonnées d'un bien (Contrat API §8, EX-120).
 */
@Service
@RequiredArgsConstructor
public class UnlockService {

    @Value("${app.unlock.unit-price-mru:500}")
    private BigDecimal defaultUnitUnlockPrice;

    private final BienRepository bienRepository;
    private final DeverrouillageRepository deverrouillageRepository;
    private final AbonnementRepository abonnementRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final BillingService billingService;
    private final NotificationService notificationService;

    @Transactional
    public UnlockResponseDto unlockProperty(UUID propertyId, UUID callerId, HttpServletRequest request) {
        Utilisateur buyer = getActiveUserOrThrow(callerId);

        Bien bien = bienRepository.findByIdAndEstArchiveFalse(propertyId)
                .filter(b -> b.getStatut() == StatutBien.publie)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "Bien immobilier introuvable ou non publié",
                        "العقار غير موجود أو غير منشور"
                ));

        Utilisateur owner = bien.getProprietaire();
        String ownerPhone = owner != null ? owner.getTelephone() : null;
        String ownerEmail = owner != null ? owner.getEmail() : null;

        // 1. Le propriétaire déverrouillant son propre bien ne paie rien
        if (owner != null && callerId.equals(owner.getId())) {
            return new UnlockResponseDto(ownerPhone, ownerEmail, BigDecimal.ZERO);
        }

        // 2. Idempotence : si déjà déverrouillé en N3 par cet utilisateur, renvoyer gratuitement les coordonnées
        if (deverrouillageRepository.existsByBienIdAndUtilisateurIdAndNiveau(
                propertyId,
                callerId,
                NiveauDeverrouillage.N3
        )) {
            return new UnlockResponseDto(ownerPhone, ownerEmail, BigDecimal.ZERO);
        }

        // 3. Vérifier si un abonnement actif couvre ce déverrouillage
        BigDecimal prixPaye = defaultUnitUnlockPrice != null ? defaultUnitUnlockPrice : new BigDecimal("500");
        Optional<Abonnement> activeSubOpt = abonnementRepository
                .findFirstByUtilisateurIdAndStatutAndDateFinGreaterThanEqualOrderByDateDebutDesc(
                        callerId,
                        StatutAbonnement.actif,
                        LocalDate.now()
                );

        if (activeSubOpt.isPresent()) {
            Abonnement sub = activeSubOpt.get();
            int inclus = sub.getFormule().getDeverrouillagesInclus() != null
                    ? sub.getFormule().getDeverrouillagesInclus()
                    : 0;
            int utilises = sub.getDeverrouillagesUtilises() != null
                    ? sub.getDeverrouillagesUtilises()
                    : 0;

            if (inclus == -1 || utilises < inclus) {
                sub.setDeverrouillagesUtilises(utilises + 1);
                abonnementRepository.save(sub);
                prixPaye = BigDecimal.ZERO;
            }
        }

        String remoteIp = extractRemoteIp(request);
        String userAgent = extractHeaderOrDefault(request, "User-Agent", "mpx-client");
        String deviceFingerprint = extractHeaderOrDefault(request, "X-Device-Id", "web-mobile-client");

        Deverrouillage unlock = Deverrouillage.builder()
                .bien(bien)
                .utilisateur(buyer)
                .niveau(NiveauDeverrouillage.N3)
                .motif(MotifDeverrouillage.frais_payes)
                .montantPaye(prixPaye)
                .adresseIp(remoteIp)
                .empreinteAppareil(deviceFingerprint)
                .agentUtilisateur(userAgent)
                .engagementSigne(true)
                .build();
        unlock = deverrouillageRepository.save(unlock);

        if (prixPaye.compareTo(BigDecimal.ZERO) > 0) {
            billingService.recordFinancialTransaction(
                    TypeTransactionFinanciere.deverrouillage,
                    prixPaye,
                    buyer,
                    bien,
                    unlock.getId()
            );
        }

        bienRepository.incrementDeverrouillages(propertyId);

        if (owner != null) {
            notificationService.createNotification(
                    owner,
                    "Coordonnées déverrouillées",
                    "Un acheteur intéressé a déverrouillé les coordonnées de votre bien « " + bien.getTitre() + " ».",
                    "deverrouillage",
                    Map.of(
                            "bien_id", bien.getId().toString(),
                            "bien_reference", bien.getReference(),
                            "acheteur_id", buyer.getId().toString()
                    )
            );
        }

        return new UnlockResponseDto(
                ownerPhone,
                ownerEmail,
                PlanDto.normalizeAmount(prixPaye)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<UnlockHistoryItemDto> listMyUnlocks(UUID callerId, int page, int perPage) {
        getActiveUserOrThrow(callerId);
        int safePage = Math.max(1, page);
        int safePerPage = Math.min(100, Math.max(1, perPage));
        PageRequest pageable = PageRequest.of(safePage - 1, safePerPage);

        Page<Deverrouillage> unlockPage = deverrouillageRepository.findByUtilisateurIdOrderByCreeLeDesc(
                callerId,
                pageable
        );

        return PageResponse.from(unlockPage.map(UnlockHistoryItemDto::fromEntity));
    }

    private String extractRemoteIp(HttpServletRequest request) {
        if (request == null) {
            return "0.0.0.0";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty() && first.length() <= 45) {
                return first;
            }
        }
        String addr = request.getRemoteAddr();
        return (addr != null && !addr.isBlank()) ? addr : "0.0.0.0";
    }

    private String extractHeaderOrDefault(HttpServletRequest request, String headerName, String fallback) {
        if (request == null) {
            return fallback;
        }
        String val = request.getHeader(headerName);
        return (val != null && !val.isBlank()) ? val.trim() : fallback;
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
