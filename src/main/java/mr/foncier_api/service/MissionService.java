package mr.foncier_api.service;

import lombok.RequiredArgsConstructor;
import mr.foncier_api.dto.common.PageResponse;
import mr.foncier_api.dto.mission.CreateMissionRequestDto;
import mr.foncier_api.dto.mission.MissionDto;
import mr.foncier_api.dto.mission.MissionPhotosResponseDto;
import mr.foncier_api.dto.mission.UpdateMissionRequestDto;
import mr.foncier_api.entity.Bien;
import mr.foncier_api.entity.Document;
import mr.foncier_api.entity.MissionTerrain;
import mr.foncier_api.entity.Utilisateur;
import mr.foncier_api.enums.StatutBien;
import mr.foncier_api.enums.StatutMissionTerrain;
import mr.foncier_api.enums.TypeMissionTerrain;
import mr.foncier_api.exception.BusinessException;
import mr.foncier_api.exception.ErrorCode;
import mr.foncier_api.repository.BienRepository;
import mr.foncier_api.repository.DocumentRepository;
import mr.foncier_api.repository.MissionTerrainRepository;
import mr.foncier_api.repository.UtilisateurRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service de gestion des missions et opérations terrain (Contrat API §7 & §11).
 */
@Service
@RequiredArgsConstructor
public class MissionService {

    private static final int MAX_PHOTOS_PER_MISSION = 15;
    private static final Set<String> SUPERVISION_ROLES = Set.of(
            "admin",
            "administrateur",
            "verificateur"
    );
    private static final Set<String> CONTRACT_MISSION_STATUSES = Set.of(
            "assignee",
            "en_cours",
            "terminee",
            "annulee"
    );

    private final MissionTerrainRepository missionTerrainRepository;
    private final BienRepository bienRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public PageResponse<MissionDto> listMissions(
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

        StatutMissionTerrain statut = (statusFilter != null && !statusFilter.isBlank())
                ? parseContractMissionStatus(statusFilter)
                : null;

        boolean isSupervision = callerRole != null && SUPERVISION_ROLES.contains(callerRole.toLowerCase());
        Page<MissionTerrain> springPage;

        if (isSupervision) {
            springPage = (statut != null)
                    ? missionTerrainRepository.findByStatutOrderByDatePlanifieeDesc(statut, pageable)
                    : missionTerrainRepository.findAllByOrderByDatePlanifieeDesc(pageable);
        } else {
            springPage = (statut != null)
                    ? missionTerrainRepository.findByAgentIdAndStatutOrderByDatePlanifieeDesc(callerId, statut, pageable)
                    : missionTerrainRepository.findByAgentIdOrderByDatePlanifieeDesc(callerId, pageable);
        }

        Page<MissionDto> dtoPage = springPage.map(m -> toMissionDto(m));
        return PageResponse.from(dtoPage);
    }

    @Transactional
    public MissionDto createMission(UUID callerId, String callerRole, CreateMissionRequestDto dto) {
        getActiveUserOrThrow(callerId);
        boolean isSupervision = callerRole != null && SUPERVISION_ROLES.contains(callerRole.toLowerCase());
        if (!isSupervision) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Rôle insuffisant : seuls un administrateur ou un vérificateur peuvent assigner une mission terrain",
                    "ليس لديك الصلاحية الكافية لتعيين مهمة ميدانية"
            );
        }

        Bien bien = bienRepository.findByIdAndEstArchiveFalse(dto.bienId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "Bien immobilier introuvable",
                        "العقار غير موجود"
                ));

        Utilisateur agent = utilisateurRepository.findById(dto.agentId())
                .filter(Utilisateur::isEstActif)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "Agent terrain introuvable ou inactif",
                        "الوكيل الميداني غير موجود أو غير نشط"
                ));

        TypeMissionTerrain typeMission = (dto.type() != null && !dto.type().isBlank())
                ? parseTypeMission(dto.type())
                : TypeMissionTerrain.verification_physique;

        Long seq = missionTerrainRepository.getNextReferenceSequenceValue();
        String ref = String.format("MIS-%d-%05d", Year.now().getValue(), seq);

        MissionTerrain mission = MissionTerrain.builder()
                .reference(ref)
                .bien(bien)
                .agent(agent)
                .type(typeMission)
                .statut(StatutMissionTerrain.assignee)
                .datePlanifiee(dto.datePlanifiee() != null ? dto.datePlanifiee() : Instant.now())
                .build();

        MissionTerrain saved = missionTerrainRepository.save(mission);

        if (bien.getStatut() == StatutBien.soumis) {
            bien.setStatut(StatutBien.en_verification);
            bienRepository.save(bien);
        }

        notificationService.createNotification(
                agent,
                "Nouvelle mission terrain assignée",
                "La mission " + ref + " vous a été assignée pour le bien « " + bien.getTitre() + " ».",
                "mission",
                Map.of(
                        "mission_id", saved.getId().toString(),
                        "mission_reference", ref,
                        "bien_id", bien.getId().toString()
                )
        );

        return toMissionDto(saved);
    }

    @Transactional(readOnly = true)
    public MissionDto getMissionDetail(UUID missionId, UUID callerId, String callerRole) {
        getActiveUserOrThrow(callerId);
        MissionTerrain mission = getMissionOrThrow(missionId);
        ensureAssignedAgentOrSupervision(mission, callerId, callerRole);
        return toMissionDto(mission);
    }

    @Transactional
    public MissionDto updateMission(
            UUID missionId,
            UUID callerId,
            String callerRole,
            UpdateMissionRequestDto dto
    ) {
        getActiveUserOrThrow(callerId);
        MissionTerrain mission = getMissionOrThrow(missionId);
        ensureAssignedAgentOrSupervision(mission, callerId, callerRole);

        if (dto.rapport() != null) {
            mission.setCompteRendu(dto.rapport().trim());
        }
        if (dto.localisationVisite() != null) {
            mission.setReleveGps(GeoUtils.toPoint(dto.localisationVisite()));
        }
        if (dto.precisionM() != null) {
            mission.setPrecisionM(dto.precisionM());
        }
        if (dto.checklist() != null) {
            mission.setChecklist(new HashMap<>(dto.checklist()));
        }

        boolean newlyCompleted = false;
        if (dto.status() != null && !dto.status().isBlank()) {
            StatutMissionTerrain targetStatus = parseContractMissionStatus(dto.status());
            if (targetStatus == StatutMissionTerrain.terminee && mission.getStatut() != StatutMissionTerrain.terminee) {
                mission.setDateRealisee(Instant.now());
                newlyCompleted = true;
            }
            mission.setStatut(targetStatus);
        }

        MissionTerrain saved = missionTerrainRepository.save(mission);

        if (newlyCompleted && saved.getBien() != null && saved.getBien().getProprietaire() != null) {
            notificationService.createNotification(
                    saved.getBien().getProprietaire(),
                    "Visite de vérification terminée",
                    "La mission terrain " + saved.getReference() + " sur votre bien « " + saved.getBien().getTitre() + " » est terminée.",
                    "mission",
                    Map.of(
                            "mission_id", saved.getId().toString(),
                            "bien_id", saved.getBien().getId().toString()
                    )
            );
        }

        return toMissionDto(saved);
    }

    @Transactional
    public MissionPhotosResponseDto uploadMissionPhotos(
            UUID missionId,
            UUID callerId,
            String callerRole,
            MultipartFile[] photos
    ) {
        getActiveUserOrThrow(callerId);
        MissionTerrain mission = getMissionOrThrow(missionId);
        ensureAssignedAgentOrSupervision(mission, callerId, callerRole);

        if (photos == null || photos.length == 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Au moins une photo de vérification est requise",
                    "مطلوب صورة تحقق واحدة على الأقل",
                    Map.of("photos", List.of("Veuillez sélectionner au moins une photo"))
            );
        }

        List<String> currentPhotos = mission.getPhotos() != null
                ? new ArrayList<>(Arrays.asList(mission.getPhotos()))
                : new ArrayList<>();

        if (currentPhotos.size() + photos.length > MAX_PHOTOS_PER_MISSION) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Nombre maximum de photos dépassé (maximum 15 photos par mission)",
                    "تم تجاوز الحد الأقصى لعدد الصور (15 صورة كحد أقصى لكل مهمة)",
                    Map.of("photos", List.of("Maximum " + MAX_PHOTOS_PER_MISSION + " photos par mission"))
            );
        }

        UUID bienId = mission.getBien() != null ? mission.getBien().getId() : mission.getId();
        String subFolder = "properties/" + bienId + "/missions/" + missionId;

        for (MultipartFile file : photos) {
            FileStorageService.StoredFileResult stored = fileStorageService.storePropertyPhoto(file, subFolder, "visite");
            currentPhotos.add(stored.publicUrl());
        }

        mission.setPhotos(currentPhotos.toArray(new String[0]));
        missionTerrainRepository.save(mission);

        return new MissionPhotosResponseDto(currentPhotos);
    }

    private MissionDto toMissionDto(MissionTerrain mission) {
        List<Document> docs = mission.getBien() != null
                ? documentRepository.findByBienIdAndEstArchiveFalseOrderByDeposeLeDesc(mission.getBien().getId())
                : List.of();
        return MissionDto.fromEntity(
                mission,
                docs,
                doc -> fileStorageService.buildPublicUrl(doc.getCleStockage())
        );
    }

    private MissionTerrain getMissionOrThrow(UUID missionId) {
        return missionTerrainRepository.findById(missionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "Mission terrain introuvable",
                        "المهمة الميدانية غير موجودة"
                ));
    }

    private void ensureAssignedAgentOrSupervision(MissionTerrain mission, UUID callerId, String callerRole) {
        boolean isAssignedAgent = mission.getAgent() != null && mission.getAgent().getId().equals(callerId);
        boolean isSupervision = callerRole != null && SUPERVISION_ROLES.contains(callerRole.toLowerCase());
        if (!isAssignedAgent && !isSupervision) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Vous n'êtes pas autorisé à accéder à cette mission terrain",
                    "غير مصرح لك بالوصول إلى هذه المهمة الميدانية"
            );
        }
    }

    private TypeMissionTerrain parseTypeMission(String raw) {
        try {
            return TypeMissionTerrain.valueOf(raw.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Type de mission invalide : " + raw,
                    "نوع المهمة غير صالح",
                    Map.of("type", List.of("Valeurs acceptées: visite_initiale, reportage_media, releve_gps, constat_etat, verification_physique, visite_accompagnee"))
            );
        }
    }

    private StatutMissionTerrain parseContractMissionStatus(String raw) {
        String normalized = raw != null ? raw.trim().toLowerCase() : "";
        if (!CONTRACT_MISSION_STATUSES.contains(normalized)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Statut de mission invalide : " + raw,
                    "حالة المهمة غير صالحة",
                    Map.of("status", List.of("Valeurs acceptées: assignee, en_cours, terminee, annulee"))
            );
        }
        return StatutMissionTerrain.valueOf(normalized);
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
