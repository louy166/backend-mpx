package mr.foncier_api.service;

import lombok.RequiredArgsConstructor;
import mr.foncier_api.dto.billing.CreateSubscriptionRequestDto;
import mr.foncier_api.dto.billing.InvoiceDto;
import mr.foncier_api.dto.billing.PlanDto;
import mr.foncier_api.dto.billing.SubscriptionDto;
import mr.foncier_api.dto.common.PageResponse;
import mr.foncier_api.entity.Abonnement;
import mr.foncier_api.entity.Bien;
import mr.foncier_api.entity.FormuleAbonnement;
import mr.foncier_api.entity.TransactionFinanciere;
import mr.foncier_api.entity.Utilisateur;
import mr.foncier_api.enums.SensTransaction;
import mr.foncier_api.enums.StatutAbonnement;
import mr.foncier_api.enums.StatutTransactionFinanciere;
import mr.foncier_api.enums.TypeTransactionFinanciere;
import mr.foncier_api.exception.BusinessException;
import mr.foncier_api.exception.ErrorCode;
import mr.foncier_api.repository.AbonnementRepository;
import mr.foncier_api.repository.FormuleAbonnementRepository;
import mr.foncier_api.repository.TransactionFinanciereRepository;
import mr.foncier_api.repository.UtilisateurRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service de gestion des plans d'abonnement, souscriptions et factures (Contrat API §10, EX-183, EX-241).
 */
@Service
@RequiredArgsConstructor
public class BillingService {

    private static final Set<String> ALLOWED_PAYMENT_METHODS = Set.of("mobile_money", "carte_bancaire");
    private static final Pattern MAURITANIA_PHONE_PATTERN = Pattern.compile("^\\+222[234]\\d{7}$");

    private final FormuleAbonnementRepository formuleAbonnementRepository;
    private final AbonnementRepository abonnementRepository;
    private final TransactionFinanciereRepository transactionFinanciereRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<PlanDto> listPlans() {
        return formuleAbonnementRepository.findByEstActifTrueOrderByTarifAnnuelAsc()
                .stream()
                .map(PlanDto::fromEntity)
                .toList();
    }

    @Transactional
    public SubscriptionDto subscribe(UUID userId, CreateSubscriptionRequestDto dto) {
        Utilisateur user = getActiveUserOrThrow(userId);
        String code = dto.planId().trim().toLowerCase();

        FormuleAbonnement formule = formuleAbonnementRepository.findByCodeIgnoreCase(code)
                .filter(FormuleAbonnement::isEstActif)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Plan d'abonnement invalide : " + dto.planId(),
                        "خطة الاشتراك غير صالحة",
                        Map.of("plan_id", List.of("Plans autorisés: gratuit, basic, premium, vip"))
                ));

        boolean isPaidPlan = formule.getTarifAnnuel() != null
                && formule.getTarifAnnuel().compareTo(BigDecimal.ZERO) > 0;

        String normalizedMethod = null;
        String normalizedPhone = null;

        if (isPaidPlan) {
            if (dto.methodePaiement() == null || dto.methodePaiement().isBlank()) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Le champ methode_paiement est requis pour un plan payant",
                        "طريقة الدفع مطلوبة للخطط المدفوعة",
                        Map.of("methode_paiement", List.of("Méthodes acceptées: mobile_money, carte_bancaire"))
                );
            }
            normalizedMethod = dto.methodePaiement().trim().toLowerCase();
            if (!ALLOWED_PAYMENT_METHODS.contains(normalizedMethod)) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Méthode de paiement non supportée : " + dto.methodePaiement(),
                        "طريقة الدفع غير مدعومة",
                        Map.of("methode_paiement", List.of("Méthodes acceptées: mobile_money, carte_bancaire"))
                );
            }

            if ("mobile_money".equals(normalizedMethod)) {
                if (dto.numeroPaiement() == null || dto.numeroPaiement().isBlank()) {
                    throw new BusinessException(
                            ErrorCode.VALIDATION_ERROR,
                            "Le champ numero_paiement est requis pour un paiement Mobile Money",
                            "رقم الدفع مطلوب للدفع عبر الهاتف",
                            Map.of("numero_paiement", List.of("Format attendu: +222XXXXXXXX"))
                    );
                }
                normalizedPhone = dto.numeroPaiement().trim();
                if (!MAURITANIA_PHONE_PATTERN.matcher(normalizedPhone).matches()) {
                    throw new BusinessException(
                            ErrorCode.VALIDATION_ERROR,
                            "Format de numéro de paiement invalide",
                            "تنسيق رقم الدفع غير صالح",
                            Map.of("numero_paiement", List.of("Format invalide, attendu: +222XXXXXXXX"))
                    );
                }
            } else if (dto.numeroPaiement() != null && !dto.numeroPaiement().isBlank()) {
                normalizedPhone = dto.numeroPaiement().trim();
            }
        }

        // Résilier proprement tout abonnement actif précédent
        List<Abonnement> previousActives = abonnementRepository.findByUtilisateurIdAndStatut(
                userId,
                StatutAbonnement.actif
        );
        for (Abonnement prev : previousActives) {
            prev.setStatut(StatutAbonnement.resilie);
        }
        if (!previousActives.isEmpty()) {
            abonnementRepository.saveAll(previousActives);
        }

        LocalDate today = LocalDate.now();
        Abonnement abonnement = Abonnement.builder()
                .utilisateur(user)
                .formule(formule)
                .statut(StatutAbonnement.actif)
                .dateDebut(today)
                .dateFin(today.plusYears(1))
                .methodePaiement(normalizedMethod)
                .numeroPaiement(normalizedPhone)
                .deverrouillagesUtilises(0)
                .reconductionTaciteAcceptee(false)
                .build();
        abonnement = abonnementRepository.save(abonnement);

        user.setPlan(formule.getCode());
        utilisateurRepository.save(user);

        if (isPaidPlan) {
            recordFinancialTransaction(
                    TypeTransactionFinanciere.abonnement,
                    formule.getTarifAnnuel(),
                    user,
                    null,
                    abonnement.getId()
            );
        }

        notificationService.createNotification(
                user,
                "Abonnement " + formule.getNomFr() + " activé",
                "Votre formule " + formule.getNomFr() + " est désormais active.",
                "abonnement",
                Map.of(
                        "plan_id", formule.getCode(),
                        "abonnement_id", abonnement.getId().toString()
                )
        );

        return SubscriptionDto.fromEntity(abonnement);
    }

    @Transactional(readOnly = true)
    public SubscriptionDto getMyActiveSubscription(UUID userId) {
        Utilisateur user = getActiveUserOrThrow(userId);
        Optional<Abonnement> activeOpt = abonnementRepository
                .findFirstByUtilisateurIdAndStatutAndDateFinGreaterThanEqualOrderByDateDebutDesc(
                        userId,
                        StatutAbonnement.actif,
                        LocalDate.now()
                );

        if (activeOpt.isPresent()) {
            return SubscriptionDto.fromEntity(activeOpt.get());
        }

        String planCode = user.getPlan() != null && !user.getPlan().isBlank()
                ? user.getPlan().trim().toLowerCase()
                : "gratuit";

        FormuleAbonnement fallbackPlan = formuleAbonnementRepository.findByCodeIgnoreCase(planCode)
                .or(() -> formuleAbonnementRepository.findByCodeIgnoreCase("gratuit"))
                .orElseGet(() -> FormuleAbonnement.builder()
                        .code("gratuit")
                        .nomFr("Gratuit")
                        .nomAr("مجاني")
                        .tarifAnnuel(BigDecimal.ZERO)
                        .limiteAnnonces(2)
                        .deverrouillagesInclus(0)
                        .build());

        return SubscriptionDto.defaultFromPlan(fallbackPlan);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceDto> listMyInvoices(UUID userId, int page, int perPage) {
        getActiveUserOrThrow(userId);
        int safePage = Math.max(1, page);
        int safePerPage = Math.min(100, Math.max(1, perPage));
        PageRequest pageable = PageRequest.of(safePage - 1, safePerPage);

        Page<TransactionFinanciere> txPage = transactionFinanciereRepository
                .findByTiersIdAndEstArchiveFalseOrderByCreeLeDesc(userId, pageable);

        return PageResponse.from(txPage.map(InvoiceDto::fromEntity));
    }

    @Transactional(readOnly = true)
    public int resolveEffectiveListingLimit(Utilisateur user) {
        Optional<Abonnement> activeOpt = abonnementRepository
                .findFirstByUtilisateurIdAndStatutAndDateFinGreaterThanEqualOrderByDateDebutDesc(
                        user.getId(),
                        StatutAbonnement.actif,
                        LocalDate.now()
                );
        if (activeOpt.isPresent() && activeOpt.get().getFormule().getLimiteAnnonces() != null) {
            return activeOpt.get().getFormule().getLimiteAnnonces();
        }

        String planCode = user.getPlan() != null && !user.getPlan().isBlank()
                ? user.getPlan().trim().toLowerCase()
                : "gratuit";

        return formuleAbonnementRepository.findByCodeIgnoreCase(planCode)
                .map(FormuleAbonnement::getLimiteAnnonces)
                .orElse(2);
    }

    @Transactional
    public TransactionFinanciere recordFinancialTransaction(
            TypeTransactionFinanciere type,
            BigDecimal montantTtc,
            Utilisateur tiers,
            Bien bien,
            UUID pieceId
    ) {
        Long seq = transactionFinanciereRepository.getNextReferenceSequenceValue();
        String ref = String.format("FAC-%d-%05d", Year.now().getValue(), seq);

        TransactionFinanciere tx = TransactionFinanciere.builder()
                .reference(ref)
                .type(type)
                .sens(SensTransaction.encaissement)
                .montantHt(montantTtc)
                .taxe(BigDecimal.ZERO)
                .montantTtc(montantTtc)
                .tiers(tiers)
                .bien(bien)
                .statut(StatutTransactionFinanciere.paye)
                .pieceId(pieceId)
                .build();

        return transactionFinanciereRepository.save(tx);
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
