package mr.foncier_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.foncier_api.dto.user.DeviceTokenRequestDto;
import mr.foncier_api.dto.user.KycSubmissionResponseDto;
import mr.foncier_api.dto.user.PhotoUploadResponseDto;
import mr.foncier_api.dto.user.UpdateProfileRequestDto;
import mr.foncier_api.dto.user.UpdateRoleRequestDto;
import mr.foncier_api.dto.user.UserDto;
import mr.foncier_api.entity.Document;
import mr.foncier_api.entity.Utilisateur;
import mr.foncier_api.enums.CategorieDocument;
import mr.foncier_api.enums.Langue;
import mr.foncier_api.enums.NiveauConfidentialite;
import mr.foncier_api.enums.RoleUtilisateur;
import mr.foncier_api.enums.StatutKyc;
import mr.foncier_api.enums.StatutVerificationDocument;
import mr.foncier_api.exception.BusinessException;
import mr.foncier_api.exception.ErrorCode;
import mr.foncier_api.repository.DocumentRepository;
import mr.foncier_api.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service métier pour la gestion des profils utilisateurs, rôles, KYC et push tokens
 * (Contrat API §3 & §9).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /**
     * Rôles autorisés en changement self-service via PUT /users/me/role (Contrat API §3).
     * Le rôle 'admin' est strictement interdit via cet endpoint.
     */
    private static final Set<String> ALLOWED_SELF_SERVICE_ROLES = Set.of(
            "particulier",
            "proprietaire",
            "apporteur",
            "agent_terrain",
            "negociateur",
            "verificateur",
            "gestionnaire_locatif"
    );

    private final UtilisateurRepository utilisateurRepository;
    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public UserDto getCurrentUserProfile(UUID userId) {
        Utilisateur user = getActiveUserOrThrow(userId);
        return UserDto.fromEntity(user);
    }

    @Transactional
    public UserDto updateProfile(UUID userId, UpdateProfileRequestDto dto) {
        Utilisateur user = getActiveUserOrThrow(userId);

        if (dto.nom() != null) {
            user.setNom(dto.nom().trim());
        }
        if (dto.prenom() != null) {
            user.setPrenom(dto.prenom().trim());
        }
        if (dto.email() != null) {
            String trimmedEmail = dto.email().trim();
            if (!trimmedEmail.isEmpty()) {
                if (utilisateurRepository.existsByEmailIgnoreCaseAndIdNot(trimmedEmail, userId)) {
                    throw new BusinessException(
                            ErrorCode.CONFLICT,
                            "Cette adresse email est déjà utilisée par un autre compte",
                            "عنوان البريد الإلكتروني هذا مستخدم بالفعل من قبل حساب آخر",
                            Map.of("email", List.of("Email déjà utilisé"))
                    );
                }
                user.setEmail(trimmedEmail);
            } else {
                user.setEmail(null);
            }
        }
        if (dto.langue() != null) {
            user.setLangue(Langue.valueOf(dto.langue().trim().toLowerCase()));
        }

        Utilisateur saved = utilisateurRepository.save(user);
        return UserDto.fromEntity(saved);
    }

    @Transactional
    public UserDto updateRole(UUID userId, UpdateRoleRequestDto dto) {
        Utilisateur user = getActiveUserOrThrow(userId);
        String requestedRole = dto.role().trim().toLowerCase();

        // Contrat API §3 : Le rôle admin ne peut être assigné que côté serveur
        if ("admin".equals(requestedRole) || "administrateur".equals(requestedRole)) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Le rôle admin ne peut être assigné que côté serveur",
                    "لا يمكن تعيين دور المسؤول إلا من جانب الخادم"
            );
        }

        if (!ALLOWED_SELF_SERVICE_ROLES.contains(requestedRole)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Rôle invalide",
                    "الدور غير صالح",
                    Map.of("role", List.of("Rôles valides: " + String.join(", ", ALLOWED_SELF_SERVICE_ROLES)))
            );
        }

        RoleUtilisateur newRole = RoleUtilisateur.valueOf(requestedRole);
        user.setRole(newRole);

        // Maintenir la liste des rôles détenus dans roles[]
        Set<String> rolesSet = new LinkedHashSet<>();
        if (user.getRoles() != null) {
            rolesSet.addAll(Arrays.asList(user.getRoles()));
        }
        rolesSet.add(requestedRole);
        user.setRoles(rolesSet.toArray(new String[0]));

        Utilisateur saved = utilisateurRepository.save(user);
        return UserDto.fromEntity(saved);
    }

    @Transactional
    public PhotoUploadResponseDto uploadProfilePhoto(UUID userId, MultipartFile photo) {
        Utilisateur user = getActiveUserOrThrow(userId);
        FileStorageService.StoredFileResult stored = fileStorageService.storeImage(
                photo,
                "users/" + userId,
                "photo"
        );

        user.setPhotoUrl(stored.publicUrl());
        utilisateurRepository.save(user);

        return new PhotoUploadResponseDto(stored.publicUrl());
    }

    @Transactional
    public KycSubmissionResponseDto submitKyc(
            UUID userId,
            String nni,
            MultipartFile recto,
            MultipartFile verso,
            MultipartFile selfie
    ) {
        Utilisateur user = getActiveUserOrThrow(userId);

        if (nni == null || !nni.trim().matches("^[0-9]{10}$")) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Le NNI doit comporter exactement 10 chiffres",
                    "يجب أن يتكون الرقم الوطني للتعريف (NNI) من 10 أرقام بالضبط",
                    Map.of("nni", List.of("Format invalide, attendu: 10 chiffres"))
            );
        }

        String cleanNni = nni.trim();
        String kycFolder = "kyc/" + userId;

        FileStorageService.StoredFileResult storedRecto = fileStorageService.storeKycDocument(recto, kycFolder, "recto");
        FileStorageService.StoredFileResult storedVerso = fileStorageService.storeKycDocument(verso, kycFolder, "verso");
        FileStorageService.StoredFileResult storedSelfie = fileStorageService.storeImage(selfie, kycFolder, "selfie");

        Document docRecto = saveKycDocumentEntity(userId, storedRecto);
        saveKycDocumentEntity(userId, storedVerso);
        saveKycDocumentEntity(userId, storedSelfie);

        user.setNni(cleanNni);
        user.setKycRectoUrl(storedRecto.publicUrl());
        user.setKycVersoUrl(storedVerso.publicUrl());
        user.setKycSelfieUrl(storedSelfie.publicUrl());
        user.setPieceIdentiteId(docRecto.getId());
        user.setStatutKyc(StatutKyc.en_cours);
        user.setKycVerifie(false);

        utilisateurRepository.save(user);

        return new KycSubmissionResponseDto(false, "Documents soumis, verification en cours");
    }

    @Transactional
    public void registerDeviceToken(UUID userId, DeviceTokenRequestDto dto) {
        Utilisateur user = getActiveUserOrThrow(userId);
        String token = dto.deviceToken().trim();

        Set<String> tokens = new LinkedHashSet<>();
        if (user.getDeviceTokens() != null) {
            tokens.addAll(Arrays.asList(user.getDeviceTokens()));
        }
        tokens.add(token);
        user.setDeviceTokens(tokens.toArray(new String[0]));

        utilisateurRepository.save(user);
    }

    private Document saveKycDocumentEntity(UUID userId, FileStorageService.StoredFileResult stored) {
        Document doc = Document.builder()
                .utilisateurId(userId)
                .categorie(CategorieDocument.piece_identite)
                .nomFichier(stored.originalFilename())
                .mime(stored.contentType())
                .taille(stored.size())
                .cleStockage(stored.storageKey())
                .empreinteSha256(stored.sha256())
                .statutVerification(StatutVerificationDocument.depose)
                .confidentialite(NiveauConfidentialite.CONFIDENTIEL)
                .deposePar(userId)
                .build();
        return documentRepository.save(doc);
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
