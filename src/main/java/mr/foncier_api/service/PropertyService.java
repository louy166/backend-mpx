package mr.foncier_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.foncier_api.dto.common.PageResponse;
import mr.foncier_api.dto.property.CreatePropertyRequestDto;
import mr.foncier_api.dto.property.PropertyDetailDto;
import mr.foncier_api.dto.property.PropertyDocumentDto;
import mr.foncier_api.dto.property.PropertyPhotosResponseDto;
import mr.foncier_api.dto.property.PropertySearchCriteria;
import mr.foncier_api.dto.property.PropertySummaryDto;
import mr.foncier_api.dto.property.UpdatePropertyRequestDto;
import mr.foncier_api.dto.property.UpdatePropertyStatusRequestDto;
import mr.foncier_api.entity.Bien;
import mr.foncier_api.entity.Document;
import mr.foncier_api.entity.Utilisateur;
import mr.foncier_api.enums.CategorieDocument;
import mr.foncier_api.enums.NiveauConfidentialite;
import mr.foncier_api.enums.NiveauDeverrouillage;
import mr.foncier_api.enums.StatutBien;
import mr.foncier_api.enums.StatutVerificationDocument;
import mr.foncier_api.enums.TypeBien;
import mr.foncier_api.enums.TypeTransactionBien;
import mr.foncier_api.exception.BusinessException;
import mr.foncier_api.exception.ErrorCode;
import mr.foncier_api.repository.BienRepository;
import mr.foncier_api.repository.BienRepositoryCustom;
import mr.foncier_api.repository.DeverrouillageRepository;
import mr.foncier_api.repository.DocumentRepository;
import mr.foncier_api.repository.FavoriRepository;
import mr.foncier_api.repository.UtilisateurRepository;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service métier pour les Biens immobiliers, la recherche géospatiale PostGIS et l'upload de documents/photos
 * (Contrat API §4, §5, §10, EX-002, EX-030, EX-031, EX-033, EX-036, EX-120).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyService {

    private static final int MAX_PHOTOS_PER_PROPERTY = 15;
    private static final Set<String> PRIVILEGED_BACKOFFICE_ROLES = Set.of(
            "admin",
            "administrateur",
            "verificateur",
            "agent_terrain",
            "negociateur",
            "gestionnaire_locatif"
    );
    private static final Set<String> MODERATION_ROLES = Set.of(
            "admin",
            "administrateur",
            "verificateur"
    );
    private static final Set<StatutBien> OWNER_ALLOWED_STATUSES = Set.of(
            StatutBien.brouillon,
            StatutBien.soumis,
            StatutBien.archive
    );
    private static final Set<String> CONTRACT_DOCUMENT_TYPES = Set.of(
            "titre_foncier",
            "acte_vente",
            "permis_construire",
            "photo",
            "plan",
            "autre"
    );

    private final BienRepository bienRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final FavoriRepository favoriRepository;
    private final DocumentRepository documentRepository;
    private final DeverrouillageRepository deverrouillageRepository;
    private final FileStorageService fileStorageService;
    private final BillingService billingService;

    @Transactional(readOnly = true)
    public PageResponse<PropertySummaryDto> searchPublicCatalog(
            PropertySearchCriteria criteria,
            UUID callerId,
            boolean featuredOnly
    ) {
        validateSearchCriteria(criteria);

        BienRepositoryCustom.SearchPageResult result = bienRepository.searchPublicCatalog(criteria, featuredOnly);
        int page = Math.max(1, criteria.page());
        int perPage = Math.min(100, Math.max(1, criteria.perPage()));
        int totalPages = (int) Math.ceil((double) result.total() / perPage);

        if (result.items().isEmpty()) {
            return new PageResponse<>(List.of(), result.total(), page, perPage, totalPages);
        }

        List<UUID> bienIds = result.items().stream()
                .map(item -> item.bien().getId())
                .toList();

        Set<UUID> favIds = (callerId != null && !bienIds.isEmpty())
                ? favoriRepository.findFavoriteBienIds(callerId, bienIds)
                : Collections.emptySet();

        // EX-030 : Dans le catalogue public, la position retournée est toujours la position floutée (sauf propriétaire)
        List<PropertySummaryDto> data = result.items().stream()
                .map(item -> {
                    Bien b = item.bien();
                    boolean isOwner = callerId != null
                            && b.getProprietaire() != null
                            && callerId.equals(b.getProprietaire().getId());
                    return PropertySummaryDto.fromEntity(
                            b,
                            isOwner,
                            favIds.contains(b.getId()),
                            item.distanceKm()
                    );
                })
                .toList();

        return new PageResponse<>(data, result.total(), page, perPage, totalPages);
    }

    @Transactional
    public PropertyDetailDto getPropertyDetail(UUID propertyId, UUID callerId, String callerRole) {
        Bien bien = bienRepository.findByIdAndEstArchiveFalse(propertyId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "Bien immobilier introuvable",
                        "العقار غير موجود"
                ));

        boolean isOwner = callerId != null
                && bien.getProprietaire() != null
                && callerId.equals(bien.getProprietaire().getId());
        boolean isApporteur = callerId != null
                && bien.getApporteur() != null
                && callerId.equals(bien.getApporteur().getId());
        boolean isBackoffice = callerRole != null && PRIVILEGED_BACKOFFICE_ROLES.contains(callerRole.toLowerCase());

        // Si le bien n'est pas publié, seuls le propriétaire, l'apporteur ou le back-office peuvent le voir
        if (bien.getStatut() != StatutBien.publie && !isOwner && !isApporteur && !isBackoffice) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "Bien immobilier introuvable",
                    "العقار غير موجود"
            );
        }

        // Contrat API §4 MUST : Incrémenter le compteur de vues à chaque appel
        bienRepository.incrementVues(propertyId);
        Bien refreshed = bienRepository.findByIdAndEstArchiveFalse(propertyId).orElse(bien);

        boolean hasN3Unlock = callerId != null && deverrouillageRepository.existsByBienIdAndUtilisateurIdAndNiveau(
                propertyId,
                callerId,
                NiveauDeverrouillage.N3
        );
        boolean exposeExact = isOwner || isBackoffice || hasN3Unlock;
        boolean estFavori = callerId != null && favoriRepository.existsByUtilisateurIdAndBienId(callerId, propertyId);
        List<Document> docs = documentRepository.findByBienIdAndEstArchiveFalseOrderByDeposeLeDesc(propertyId);

        return PropertyDetailDto.fromEntity(
                refreshed,
                exposeExact,
                estFavori,
                docs,
                doc -> resolveDocumentUrl(doc, exposeExact)
        );
    }

    @Transactional
    public PropertyDetailDto createProperty(UUID callerId, String callerRole, CreatePropertyRequestDto dto) {
        Utilisateur owner = getActiveUserOrThrow(callerId);
        boolean isBackoffice = callerRole != null && PRIVILEGED_BACKOFFICE_ROLES.contains(callerRole.toLowerCase());

        if (!isBackoffice) {
            int limit = billingService.resolveEffectiveListingLimit(owner);
            if (limit != -1) {
                long currentCount = bienRepository.countByProprietaireIdAndEstArchiveFalse(owner.getId());
                if (currentCount >= limit) {
                    throw new BusinessException(
                            ErrorCode.FORBIDDEN,
                            "Limite d'annonces atteinte pour votre plan actuel (" + limit + " annonces max). Veuillez souscrire à un plan supérieur.",
                            "تم الوصول إلى الحد الأقصى للإعلانات في خطتك الحالية"
                    );
                }
            }
        }

        TypeBien typeBien = parseTypeBien(dto.type());
        TypeTransactionBien transaction = parseTransaction(dto.transaction());
        boolean isModerationUser = callerRole != null && MODERATION_ROLES.contains(callerRole.toLowerCase());

        StatutBien targetStatus = StatutBien.brouillon;
        if (dto.status() != null && !dto.status().isBlank()) {
            targetStatus = parseStatutBien(dto.status());
            if (!isModerationUser && targetStatus != StatutBien.brouillon && targetStatus != StatutBien.soumis) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "À la création, le statut doit être 'brouillon' ou 'soumis'",
                        "عند الإنشاء، يجب أن تكون الحالة 'brouillon' أو 'soumis'",
                        Map.of("status", List.of("Valeurs autorisées à la création: brouillon, soumis"))
                );
            }
        }

        Point exactPoint = GeoUtils.toPoint(dto.localisation());
        int rayonFlou = 300;
        Point fuzzyPoint = GeoUtils.generateFuzzyPoint(exactPoint, rayonFlou);

        String reference = generateNextReference();

        Bien bien = Bien.builder()
                .reference(reference)
                .titre(dto.titre().trim())
                .descriptionFr(dto.description().trim())
                .descriptionAr(dto.descriptionAr() != null ? dto.descriptionAr().trim() : null)
                .type(typeBien)
                .transaction(transaction)
                .prix(dto.prix())
                .devise(dto.devise() != null && !dto.devise().isBlank() ? dto.devise().trim().toUpperCase() : "MRU")
                .superficieM2(dto.surfaceM2())
                .nbPieces(dto.nombrePieces())
                .nbChambres(dto.nombreChambres())
                .nbSdb(dto.nombreSallesBain())
                .adresse(dto.adresse() != null ? dto.adresse().trim() : null)
                .ville(dto.ville() != null ? dto.ville().trim() : null)
                .quartierNom(dto.quartier() != null ? dto.quartier().trim() : null)
                .positionExacte(exactPoint)
                .positionFloue(fuzzyPoint)
                .rayonFlouM(rayonFlou)
                .statut(targetStatus)
                .publieLe(targetStatus == StatutBien.publie ? Instant.now() : null)
                .proprietaire(owner)
                .build();

        Bien saved = bienRepository.save(bien);
        return PropertyDetailDto.fromEntity(saved, true, false, List.of(), doc -> resolveDocumentUrl(doc, true));
    }

    @Transactional
    public PropertyDetailDto updateProperty(
            UUID propertyId,
            UUID callerId,
            String callerRole,
            UpdatePropertyRequestDto dto
    ) {
        getActiveUserOrThrow(callerId);
        Bien bien = getBienOrThrow(propertyId);
        ensureOwnerOrAdmin(bien, callerId, callerRole);

        if (dto.titre() != null && !dto.titre().isBlank()) {
            bien.setTitre(dto.titre().trim());
        }
        if (dto.description() != null && !dto.description().isBlank()) {
            bien.setDescriptionFr(dto.description().trim());
        }
        if (dto.descriptionAr() != null) {
            bien.setDescriptionAr(dto.descriptionAr().trim());
        }
        if (dto.type() != null && !dto.type().isBlank()) {
            bien.setType(parseTypeBien(dto.type()));
        }
        if (dto.transaction() != null && !dto.transaction().isBlank()) {
            bien.setTransaction(parseTransaction(dto.transaction()));
        }
        if (dto.prix() != null) {
            bien.setPrix(dto.prix());
        }
        if (dto.devise() != null && !dto.devise().isBlank()) {
            bien.setDevise(dto.devise().trim().toUpperCase());
        }
        if (dto.surfaceM2() != null) {
            bien.setSuperficieM2(dto.surfaceM2());
        }
        if (dto.nombrePieces() != null) {
            bien.setNbPieces(dto.nombrePieces());
        }
        if (dto.nombreChambres() != null) {
            bien.setNbChambres(dto.nombreChambres());
        }
        if (dto.nombreSallesBain() != null) {
            bien.setNbSdb(dto.nombreSallesBain());
        }
        if (dto.adresse() != null) {
            bien.setAdresse(dto.adresse().trim());
        }
        if (dto.ville() != null) {
            bien.setVille(dto.ville().trim());
        }
        if (dto.quartier() != null) {
            bien.setQuartierNom(dto.quartier().trim());
        }
        if (dto.localisation() != null) {
            Point exactPoint = GeoUtils.toPoint(dto.localisation());
            bien.setPositionExacte(exactPoint);
            bien.setPositionFloue(GeoUtils.generateFuzzyPoint(exactPoint, bien.getRayonFlouM()));
        }
        if (dto.status() != null && !dto.status().isBlank()) {
            StatutBien newStatus = parseStatutBien(dto.status());
            boolean isModerationUser = callerRole != null && MODERATION_ROLES.contains(callerRole.toLowerCase());
            if (!isModerationUser && !OWNER_ALLOWED_STATUSES.contains(newStatus)) {
                throw new BusinessException(
                        ErrorCode.FORBIDDEN,
                        "Seul un vérificateur ou administrateur peut définir le statut '" + newStatus.name() + "'",
                        "فقط المدقق أو المسؤول يمكنه تعيين هذه الحالة"
                );
            }
            if (newStatus == StatutBien.archive) {
                bien.setEstArchive(true);
                bien.setArchiveLe(Instant.now());
            } else if (newStatus == StatutBien.publie && bien.getPublieLe() == null) {
                bien.setPublieLe(Instant.now());
                if (bien.getPositionExacte() != null) {
                    bien.setPositionFloue(GeoUtils.generateFuzzyPoint(bien.getPositionExacte(), bien.getRayonFlouM()));
                }
            }
            bien.setStatut(newStatus);
        }

        Bien saved = bienRepository.save(bien);
        boolean estFavori = favoriRepository.existsByUtilisateurIdAndBienId(callerId, propertyId);
        List<Document> docs = documentRepository.findByBienIdAndEstArchiveFalseOrderByDeposeLeDesc(propertyId);
        return PropertyDetailDto.fromEntity(saved, true, estFavori, docs, doc -> resolveDocumentUrl(doc, true));
    }

    @Transactional
    public PropertyDocumentDto uploadPropertyDocument(
            UUID propertyId,
            UUID callerId,
            String callerRole,
            String rawType,
            MultipartFile file
    ) {
        getActiveUserOrThrow(callerId);
        Bien bien = getBienOrThrow(propertyId);
        ensureOwnerOrAdmin(bien, callerId, callerRole);

        CategorieDocument categorie = parseContractDocumentType(rawType);
        String subFolder = "properties/" + propertyId + "/documents";
        FileStorageService.StoredFileResult stored = fileStorageService.storePropertyDocument(
                file,
                subFolder,
                categorie.name()
        );

        NiveauConfidentialite confidentialite = (categorie == CategorieDocument.photo || categorie == CategorieDocument.plan)
                ? NiveauConfidentialite.PUBLIC
                : NiveauConfidentialite.RESTREINT;

        Document doc = Document.builder()
                .bienId(propertyId)
                .categorie(categorie)
                .nomFichier(stored.originalFilename())
                .mime(stored.contentType())
                .taille(stored.size())
                .cleStockage(stored.storageKey())
                .empreinteSha256(stored.sha256())
                .statutVerification(StatutVerificationDocument.depose)
                .confidentialite(confidentialite)
                .deposePar(callerId)
                .deposeLe(Instant.now())
                .build();

        Document savedDoc = documentRepository.save(doc);
        return PropertyDocumentDto.fromEntity(savedDoc, stored.publicUrl());
    }

    @Transactional
    public PropertyPhotosResponseDto uploadPropertyPhotos(
            UUID propertyId,
            UUID callerId,
            String callerRole,
            MultipartFile[] photos
    ) {
        getActiveUserOrThrow(callerId);
        Bien bien = getBienOrThrow(propertyId);
        ensureOwnerOrAdmin(bien, callerId, callerRole);

        if (photos == null || photos.length == 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Au moins une photo est requise",
                    "مطلوب صورة واحدة على الأقل",
                    Map.of("photos", List.of("Veuillez sélectionner au moins une photo"))
            );
        }

        List<String> currentPhotos = bien.getPhotos() != null
                ? new ArrayList<>(Arrays.asList(bien.getPhotos()))
                : new ArrayList<>();

        if (currentPhotos.size() + photos.length > MAX_PHOTOS_PER_PROPERTY) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Nombre maximum de photos dépassé (maximum 15 photos par bien)",
                    "تم تجاوز الحد الأقصى لعدد الصور (15 صورة كحد أقصى لكل عقار)",
                    Map.of("photos", List.of("Maximum " + MAX_PHOTOS_PER_PROPERTY + " photos par bien"))
            );
        }

        String subFolder = "properties/" + propertyId;
        for (MultipartFile file : photos) {
            FileStorageService.StoredFileResult stored = fileStorageService.storePropertyPhoto(file, subFolder, "photo");
            currentPhotos.add(stored.publicUrl());
        }

        bien.setPhotos(currentPhotos.toArray(new String[0]));
        bienRepository.save(bien);

        return new PropertyPhotosResponseDto(currentPhotos);
    }

    @Transactional
    public PropertyDetailDto updatePropertyStatus(
            UUID propertyId,
            UUID callerId,
            String callerRole,
            UpdatePropertyStatusRequestDto dto
    ) {
        getActiveUserOrThrow(callerId);
        if (callerRole == null || !MODERATION_ROLES.contains(callerRole.toLowerCase())) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Rôle insuffisant : seuls un vérificateur ou un administrateur peuvent modérer le statut d'un bien",
                    "ليس لديك الصلاحية الكافية لتغيير حالة العقار"
            );
        }

        Bien bien = getBienOrThrow(propertyId);
        StatutBien newStatus = parseStatutBien(dto.status());

        if ((newStatus == StatutBien.vendu || newStatus == StatutBien.loue)) {
            BigDecimal prixConclu = dto.prixConclu() != null ? dto.prixConclu() : bien.getPrixConclu();
            if (prixConclu == null || prixConclu.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "Le champ prix_conclu est obligatoire pour passer un bien à l'état vendu ou loué",
                        "حقل السعر المتفق عليه إلزامي عند البيع أو الإيجار",
                        Map.of("prix_conclu", List.of("Doit être supérieur à 0"))
                );
            }
            bien.setPrixConclu(prixConclu);
            bien.setDateConclusion(LocalDate.now());
        }

        if (dto.estVerifie() != null) {
            bien.setEstVerifie(dto.estVerifie());
        } else if (newStatus == StatutBien.verifie) {
            bien.setEstVerifie(true);
        }

        if (dto.estVip() != null) {
            bien.setEstVip(dto.estVip());
        }

        if (dto.reserves() != null) {
            bien.setReserves(dto.reserves().trim());
        }

        if (newStatus == StatutBien.publie) {
            bien.setPublieLe(Instant.now());
            // EX-031 : Recalcul du point flou à chaque publication
            if (bien.getPositionExacte() != null) {
                bien.setPositionFloue(GeoUtils.generateFuzzyPoint(bien.getPositionExacte(), bien.getRayonFlouM()));
            }
        } else if (newStatus == StatutBien.archive) {
            bien.setEstArchive(true);
            bien.setArchiveLe(Instant.now());
        }

        bien.setStatut(newStatus);
        Bien saved = bienRepository.save(bien);
        boolean estFavori = favoriRepository.existsByUtilisateurIdAndBienId(callerId, propertyId);
        List<Document> docs = documentRepository.findByBienIdAndEstArchiveFalseOrderByDeposeLeDesc(propertyId);

        return PropertyDetailDto.fromEntity(saved, true, estFavori, docs, doc -> resolveDocumentUrl(doc, true));
    }

    @Transactional(readOnly = true)
    public PageResponse<PropertySummaryDto> listMyProperties(
            UUID callerId,
            String statusFilter,
            int page,
            int perPage
    ) {
        getActiveUserOrThrow(callerId);
        int safePage = Math.max(1, page);
        int safePerPage = Math.min(100, Math.max(1, perPage));
        PageRequest pageable = PageRequest.of(safePage - 1, safePerPage);

        Page<Bien> springPage;
        if (statusFilter != null && !statusFilter.isBlank()) {
            StatutBien statut = parseStatutBien(statusFilter);
            springPage = bienRepository.findByProprietaireIdAndStatutAndEstArchiveFalseOrderByCreeLeDesc(
                    callerId,
                    statut,
                    pageable
            );
        } else {
            springPage = bienRepository.findByProprietaireIdAndEstArchiveFalseOrderByCreeLeDesc(callerId, pageable);
        }

        List<UUID> bienIds = springPage.getContent().stream().map(Bien::getId).toList();
        Set<UUID> favIds = !bienIds.isEmpty()
                ? favoriRepository.findFavoriteBienIds(callerId, bienIds)
                : Collections.emptySet();

        Page<PropertySummaryDto> dtoPage = springPage.map(
                b -> PropertySummaryDto.fromEntity(b, true, favIds.contains(b.getId()), null)
        );
        return PageResponse.from(dtoPage);
    }

    private String resolveDocumentUrl(Document doc, boolean exposeRestricted) {
        if (doc == null) {
            return null;
        }
        if (exposeRestricted || doc.getConfidentialite() == NiveauConfidentialite.PUBLIC) {
            return fileStorageService.buildPublicUrl(doc.getCleStockage());
        }
        return null;
    }

    private CategorieDocument parseContractDocumentType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Le champ 'type' est requis",
                    "الحقل 'type' مطلوب",
                    Map.of("type", List.of("Valeurs acceptées: titre_foncier, acte_vente, permis_construire, photo, plan, autre"))
            );
        }
        String normalized = rawType.trim().toLowerCase();
        if (!CONTRACT_DOCUMENT_TYPES.contains(normalized)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Type de document invalide : " + rawType,
                    "نوع المستند غير صالح",
                    Map.of("type", List.of("Valeurs acceptées: titre_foncier, acte_vente, permis_construire, photo, plan, autre"))
            );
        }
        return CategorieDocument.valueOf(normalized);
    }

    private void validateSearchCriteria(PropertySearchCriteria criteria) {
        if (criteria.type() != null && !criteria.type().isBlank()) {
            parseTypeBien(criteria.type());
        }
        if (criteria.transaction() != null && !criteria.transaction().isBlank()) {
            parseTransaction(criteria.transaction());
        }
        if ((criteria.lat() != null && criteria.lng() == null)
                || (criteria.lat() == null && criteria.lng() != null)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Les paramètres 'lat' et 'lng' doivent être fournis ensemble",
                    "يجب تقديم المعلمتين 'lat' و 'lng' معاً",
                    Map.of("localisation", List.of("Fournir à la fois lat et lng"))
            );
        }
        if (criteria.lat() != null && criteria.lng() != null) {
            GeoUtils.validateCoordinates(criteria.lat(), criteria.lng());
        }
        if (criteria.prixMin() != null && criteria.prixMax() != null
                && criteria.prixMin().compareTo(criteria.prixMax()) > 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "prix_min ne peut pas être supérieur à prix_max",
                    "لا يمكن أن يكون السعر الأدنى أكبر من السعر الأقصى",
                    Map.of("prix_min", List.of("Doit être inférieur ou égal à prix_max"))
            );
        }
    }

    private String generateNextReference() {
        Long seq = bienRepository.getNextReferenceSequenceValue();
        int year = Year.now().getValue();
        return String.format("FM-%d-%05d", year, seq);
    }

    private Bien getBienOrThrow(UUID propertyId) {
        return bienRepository.findByIdAndEstArchiveFalse(propertyId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "Bien immobilier introuvable",
                        "العقار غير موجود"
                ));
    }

    private void ensureOwnerOrAdmin(Bien bien, UUID callerId, String callerRole) {
        boolean isOwner = bien.getProprietaire() != null && bien.getProprietaire().getId().equals(callerId);
        boolean isAdmin = callerRole != null
                && ("admin".equalsIgnoreCase(callerRole) || "administrateur".equalsIgnoreCase(callerRole));
        if (!isOwner && !isAdmin) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Vous n'êtes pas autorisé à modifier ce bien",
                    "غير مصرح لك بتعديل هذا العقار"
            );
        }
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

    private TypeBien parseTypeBien(String raw) {
        try {
            return TypeBien.valueOf(raw.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Type de bien invalide : " + raw,
                    "نوع العقار غير صالح",
                    Map.of("type", List.of("Valeurs acceptées: terrain, maison, appartement, bureau, commercial, autre"))
            );
        }
    }

    private TypeTransactionBien parseTransaction(String raw) {
        try {
            return TypeTransactionBien.valueOf(raw.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Type de transaction invalide : " + raw,
                    "نوع المعاملة غير صالح",
                    Map.of("transaction", List.of("Valeurs acceptées: vente, location, colocation"))
            );
        }
    }

    private StatutBien parseStatutBien(String raw) {
        try {
            return StatutBien.valueOf(raw.trim().toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Statut de bien invalide : " + raw,
                    "حالة العقار غير صالحة",
                    Map.of("status", List.of("Valeurs acceptées: brouillon, soumis, en_verification, verifie, rejete, publie, archive"))
            );
        }
    }
}
