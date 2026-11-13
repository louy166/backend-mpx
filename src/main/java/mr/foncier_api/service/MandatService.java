package mr.foncier_api.service;

import lombok.RequiredArgsConstructor;
import mr.foncier_api.dto.common.PageResponse;
import mr.foncier_api.dto.mandat.CreateMandatRequestDto;
import mr.foncier_api.dto.mandat.MandatDto;
import mr.foncier_api.dto.mandat.UpdateMandatStatusRequestDto;
import mr.foncier_api.entity.Bien;
import mr.foncier_api.entity.Mandat;
import mr.foncier_api.entity.Utilisateur;
import mr.foncier_api.enums.StatutMandat;
import mr.foncier_api.enums.TypeMandat;
import mr.foncier_api.exception.BusinessException;
import mr.foncier_api.exception.ErrorCode;
import mr.foncier_api.repository.BienRepository;
import mr.foncier_api.repository.MandatRepository;
import mr.foncier_api.repository.UtilisateurRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service de gestion des mandats immobiliers (Contrat API §6 & §11).
 */
@Service
@RequiredArgsConstructor
public class MandatService {

    private static final Set<String> SUPERVISION_ROLES = Set.of(
            "admin",
            "administrateur",
            "verificateur"
    );
    private static final Set<String> AGENT_ROLES = Set.of(
            "agent_terrain",
            "negociateur",
            "gestionnaire_locatif",
            "apporteur"
    );
    private static final Set<String> CONTRACT_MANDAT_STATUSES = Set.of(
            "actif",
            "expire",
            "resilie",
            "suspendu"
    );
    private static final Set<TypeMandat> EXCLUSIVE_MANDAT_TYPES = Set.of(
            TypeMandat.exclusif,
            TypeMandat.vente_exclusive
    );

    private final MandatRepository mandatRepository;
    private final BienRepository bienRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public PageResponse<MandatDto> listMandats(
            UUID callerId,
            String callerRole,
            String statusFilter,
            int page,
            int perPage
    ) {
        getActiveUserOrThrow(callerId);
        int safePage = Math.max(1, page);
        int safePerPage = Math.min(100, Math.max(1, perPage));
        PageRequest pageable = PageRequest.of(safePage - 1, safePerPage);

        StatutMandat statut = (statusFilter != null && !statusFilter.isBlank())
                ? parseContractMandatStatus(statusFilter)
                : null;

        String role = callerRole != null ? callerRole.toLowerCase() : "";
        Page<Mandat> springPage;

        if (SUPERVISION_ROLES.contains(role)) {
            springPage = (statut != null)
                    ? mandatRepository.findByStatutAndEstArchiveFalseOrderByCreeLeDesc(statut, pageable)
                    : mandatRepository.findByEstArchiveFalseOrderByCreeLeDesc(pageable);
        } else if (AGENT_ROLES.contains(role)) {
            springPage = (statut != null)
                    ? mandatRepository.findByAgentIdAndStatutAndEstArchiveFalseOrderByCreeLeDesc(callerId, statut, pageable)
                    : mandatRepository.findByAgentIdAndEstArchiveFalseOrderByCreeLeDesc(callerId, pageable);
        } else {
            springPage = (statut != null)
                    ? mandatRepository.findByProprietaireIdAndStatutAndEstArchiveFalseOrderByCreeLeDesc(callerId, statut, pageable)
                    : mandatRepository.findByProprietaireIdAndEstArchiveFalseOrderByCreeLeDesc(callerId, pageable);
        }

        return PageResponse.from(springPage.map(MandatDto::fromEntity));
    }

    @Transactional
    public MandatDto createMandat(UUID callerId, String callerRole, CreateMandatRequestDto dto) {
        getActiveUserOrThrow(callerId);

        if (!dto.dateFin().isAfter(dto.dateDebut())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "La date de fin doit être postérieure à la date de début",
                    "يجب أن يكون تاريخ الانتهاء بعد تاريخ البدء",
                    Map.of("date_fin", List.of("Doit être strictement postérieure à date_debut"))
            );
        }

        Bien bien = bienRepository.findByIdAndEstArchiveFalse(dto.bienId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "Bien immobilier introuvable",
                        "العقار غير موجود"
                ));

        boolean isOwner = bien.getProprietaire() != null && bien.getProprietaire().getId().equals(callerId);
        boolean isAdmin = callerRole != null
                && ("admin".equalsIgnoreCase(callerRole) || "administrateur".equalsIgnoreCase(callerRole));
        if (!isOwner && !isAdmin) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Seul le propriétaire du bien ou un administrateur peut créer un mandat",
                    "فقط مالك العقار أو المسؤول يمكنه إنشاء تفويض"
            );
        }

        TypeMandat typeMandat = parseTypeMandat(dto.type());
        checkExclusivityConflict(bien.getId(), typeMandat);

        Utilisateur agent = null;
        if (dto.agentId() != null) {
            agent = utilisateurRepository.findById(dto.agentId())
                    .filter(Utilisateur::isEstActif)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.NOT_FOUND,
                            "Agent introuvable ou inactif",
                            "الوكيل غير موجود أو غير نشط"
                    ));
        }

        Long seq = mandatRepository.getNextReferenceSequenceValue();
        String ref = String.format("MAN-%d-%05d", Year.now().getValue(), seq);

        Mandat mandat = Mandat.builder()
                .reference(ref)
                .type(typeMandat)
                .bien(bien)
                .proprietaire(bien.getProprietaire())
                .agent(agent)
                .tauxCommission(dto.commissionPourcentage())
                .dateDebut(dto.dateDebut())
                .dateFin(dto.dateFin())
                .statut(StatutMandat.actif)
                .conditionsParticulieres(dto.conditionsParticulieres() != null ? dto.conditionsParticulieres().trim() : null)
                .build();

        Mandat saved = mandatRepository.save(mandat);

        bien.setMandatId(saved.getId());
        bienRepository.save(bien);

        if (agent != null && !agent.getId().equals(callerId)) {
            notificationService.createNotification(
                    agent,
                    "Nouveau mandat assigné",
                    "Un mandat " + typeMandat.name() + " (" + ref + ") vous a été confié pour le bien « " + bien.getTitre() + " ».",
                    "mandat",
                    Map.of(
                            "mandat_id", saved.getId().toString(),
                            "mandat_reference", ref,
                            "bien_id", bien.getId().toString()
                    )
            );
        }

        return MandatDto.fromEntity(saved);
    }

    @Transactional
    public MandatDto updateMandatStatus(
            UUID mandatId,
            UUID callerId,
            String callerRole,
            UpdateMandatStatusRequestDto dto
    ) {
        getActiveUserOrThrow(callerId);
        Mandat mandat = mandatRepository.findByIdAndEstArchiveFalse(mandatId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "Mandat introuvable",
                        "التفويض غير موجود"
                ));

        boolean isOwner = mandat.getProprietaire() != null && mandat.getProprietaire().getId().equals(callerId);
        boolean isAgent = mandat.getAgent() != null && mandat.getAgent().getId().equals(callerId);
        boolean isAdmin = callerRole != null
                && ("admin".equalsIgnoreCase(callerRole) || "administrateur".equalsIgnoreCase(callerRole));

        if (!isOwner && !isAgent && !isAdmin) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Vous n'êtes pas autorisé à modifier le statut de ce mandat",
                    "غير مصرح لك بتعديل حالة هذا التفويض"
            );
        }

        StatutMandat targetStatus = parseContractMandatStatus(dto.status());
        Bien bien = mandat.getBien();

        if (targetStatus == StatutMandat.actif && mandat.getStatut() != StatutMandat.actif && bien != null) {
            checkExclusivityConflict(bien.getId(), mandat.getType());
            bien.setMandatId(mandat.getId());
            bienRepository.save(bien);
        } else if ((targetStatus == StatutMandat.resilie || targetStatus == StatutMandat.expire)
                && bien != null
                && mandat.getId().equals(bien.getMandatId())) {
            bien.setMandatId(null);
            bienRepository.save(bien);
        }

        mandat.setStatut(targetStatus);
        Mandat saved = mandatRepository.save(mandat);

        Utilisateur counterpart = isOwner ? mandat.getAgent() : mandat.getProprietaire();
        if (counterpart != null && !counterpart.getId().equals(callerId)) {
            notificationService.createNotification(
                    counterpart,
                    "Statut du mandat mis à jour",
                    "Le mandat " + saved.getReference() + " est passé au statut « " + targetStatus.name() + " ».",
                    "mandat",
                    Map.of(
                            "mandat_id", saved.getId().toString(),
                            "status", targetStatus.name()
                    )
            );
        }

        return MandatDto.fromEntity(saved);
    }

    private void checkExclusivityConflict(UUID bienId, TypeMandat requestedType) {
        boolean hasExistingExclusive = mandatRepository.existsByBienIdAndStatutAndTypeInAndEstArchiveFalse(
                bienId,
                StatutMandat.actif,
                EXCLUSIVE_MANDAT_TYPES
        );
        if (hasExistingExclusive) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Un mandat exclusif actif existe déjà sur ce bien immobilier",
                    "يوجد بالفعل تفويض حصري نشط لهذا العقار"
            );
        }

        if (EXCLUSIVE_MANDAT_TYPES.contains(requestedType)) {
            boolean hasAnyActive = mandatRepository.existsByBienIdAndStatutAndEstArchiveFalse(
                    bienId,
                    StatutMandat.actif
            );
            if (hasAnyActive) {
                throw new BusinessException(
                        ErrorCode.CONFLICT,
                        "Impossible de créer un mandat exclusif tant qu'un autre mandat actif existe sur ce bien",
                        "لا يمكن إنشاء تفويض حصري مع وجود تفويض نشط آخر على هذا العقار"
                );
            }
        }
    }

    private TypeMandat parseTypeMandat(String raw) {
        try {
            return TypeMandat.valueOf(raw.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Type de mandat invalide : " + raw,
                    "نوع التفويض غير صالح",
                    Map.of("type", List.of("Valeurs acceptées: simple, exclusif, vente_simple, vente_exclusive, location, gestion_locative"))
            );
        }
    }

    private StatutMandat parseContractMandatStatus(String raw) {
        String normalized = raw != null ? raw.trim().toLowerCase() : "";
        if (!CONTRACT_MANDAT_STATUSES.contains(normalized)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Statut de mandat invalide : " + raw,
                    "حالة التفويض غير صالحة",
                    Map.of("status", List.of("Valeurs acceptées: actif, expire, resilie, suspendu"))
            );
        }
        return StatutMandat.valueOf(normalized);
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
