package mr.foncier_api.service;

import lombok.extern.slf4j.Slf4j;
import mr.foncier_api.exception.BusinessException;
import mr.foncier_api.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service de stockage de fichiers (multipart/form-data) avec calcul d'empreinte SHA-256
 * (Contrat API §3, §5 & Checklist #8).
 */
@Slf4j
@Service
public class FileStorageService {

    public static final long MAX_DEFAULT_FILE_SIZE_BYTES = 10L * 1024L * 1024L; // 10 Mo
    public static final long MAX_PROPERTY_PHOTO_SIZE_BYTES = 5L * 1024L * 1024L; // 5 Mo (Contrat API §5)

    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );
    private static final Set<String> KYC_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "application/pdf"
    );

    private final Path uploadRootPath;
    private final String publicBaseUrl;

    public FileStorageService(
            @Value("${app.storage.upload-dir:uploads}") String uploadDir,
            @Value("${app.storage.public-base-url:http://localhost:8080}") String publicBaseUrl
    ) {
        this.uploadRootPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public record StoredFileResult(
            String publicUrl,
            String storageKey,
            String originalFilename,
            String contentType,
            long size,
            String sha256
    ) {
    }

    public StoredFileResult storeImage(MultipartFile file, String subFolder, String fieldName) {
        return storeFile(file, subFolder, fieldName, IMAGE_MIME_TYPES, MAX_DEFAULT_FILE_SIZE_BYTES);
    }

    public StoredFileResult storePropertyPhoto(MultipartFile file, String subFolder, String fieldName) {
        return storeFile(file, subFolder, fieldName, IMAGE_MIME_TYPES, MAX_PROPERTY_PHOTO_SIZE_BYTES);
    }

    public StoredFileResult storeKycDocument(MultipartFile file, String subFolder, String fieldName) {
        return storeFile(file, subFolder, fieldName, KYC_MIME_TYPES, MAX_DEFAULT_FILE_SIZE_BYTES);
    }

    public StoredFileResult storePropertyDocument(MultipartFile file, String subFolder, String fieldName) {
        return storeFile(file, subFolder, fieldName, KYC_MIME_TYPES, MAX_DEFAULT_FILE_SIZE_BYTES);
    }

    public String buildPublicUrl(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        String normalized = storageKey.replace('\\', '/').replaceAll("^/+", "");
        return publicBaseUrl + "/uploads/" + normalized;
    }

    private StoredFileResult storeFile(
            MultipartFile file,
            String subFolder,
            String fieldName,
            Set<String> allowedMimeTypes,
            long maxFileSizeBytes
    ) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Le fichier '" + fieldName + "' est requis et ne peut pas être vide",
                    "الملف '" + fieldName + "' مطلوب ولا يمكن أن يكون فارغاً",
                    Map.of(fieldName, List.of("Le fichier est requis et ne peut pas être vide"))
            );
        }

        if (file.getSize() > maxFileSizeBytes) {
            long maxMb = maxFileSizeBytes / (1024L * 1024L);
            throw new BusinessException(
                    ErrorCode.FILE_TOO_LARGE,
                    "La taille du fichier dépasse la limite maximale autorisée (" + maxMb + " Mo)",
                    "حجم الملف يتجاوز الحد الأقصى المسموح به (" + maxMb + " ميجابايت)"
            );
        }

        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (!allowedMimeTypes.contains(contentType)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Type de fichier non supporté pour '" + fieldName + "'",
                    "نوع الملف غير مدعوم للحقل '" + fieldName + "'",
                    Map.of(fieldName, List.of("Formats acceptés: " + String.join(", ", allowedMimeTypes)))
            );
        }

        try {
            byte[] bytes = file.getBytes();
            String sha256 = computeSha256(bytes);
            String extension = resolveExtension(file.getOriginalFilename(), contentType);
            String fileName = fieldName + "-" + UUID.randomUUID() + extension;

            Path targetDir = uploadRootPath.resolve(subFolder).normalize();
            Files.createDirectories(targetDir);

            Path targetFile = targetDir.resolve(fileName).normalize();
            Files.write(targetFile, bytes);

            String normalizedSubFolder = subFolder.replace('\\', '/').replaceAll("^/+|/+$", "");
            String storageKey = normalizedSubFolder + "/" + fileName;
            String publicUrl = publicBaseUrl + "/uploads/" + storageKey;
            String origName = file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()
                    ? file.getOriginalFilename()
                    : fileName;

            return new StoredFileResult(publicUrl, storageKey, origName, contentType, file.getSize(), sha256);
        } catch (IOException e) {
            log.error("Erreur lors de l'enregistrement du fichier {}", fieldName, e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Impossible d'enregistrer le fichier téléversé",
                    "تعذر حفظ الملف المرفوع"
            );
        }
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponible", e);
        }
    }

    private String resolveExtension(String originalFilename, String contentType) {
        if (originalFilename != null && originalFilename.contains(".")) {
            String ext = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
            if (ext.matches("^\\.[a-z0-9]{2,5}$")) {
                return ext;
            }
        }
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> ".jpg";
        };
    }
}
