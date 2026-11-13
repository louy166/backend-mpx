package mr.foncier_api.service;

import lombok.RequiredArgsConstructor;
import mr.foncier_api.dto.common.PageResponse;
import mr.foncier_api.dto.notification.NotificationDto;
import mr.foncier_api.entity.Notification;
import mr.foncier_api.entity.Utilisateur;
import mr.foncier_api.exception.BusinessException;
import mr.foncier_api.exception.ErrorCode;
import mr.foncier_api.repository.NotificationRepository;
import mr.foncier_api.repository.UtilisateurRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service de gestion des notifications in-app (Contrat API §9).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UtilisateurRepository utilisateurRepository;

    @Transactional
    public Notification createNotification(
            Utilisateur destinataire,
            String titre,
            String message,
            String type,
            Map<String, Object> metadata
    ) {
        Notification notification = Notification.builder()
                .utilisateur(destinataire)
                .titre(titre)
                .message(message)
                .type(type != null && !type.isBlank() ? type : "info")
                .lu(false)
                .metadata(metadata != null ? new HashMap<>(metadata) : new HashMap<>())
                .build();
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> listMyNotifications(UUID userId, Boolean lu, int page, int perPage) {
        ensureActiveUser(userId);
        int safePage = Math.max(1, page);
        int safePerPage = Math.min(100, Math.max(1, perPage));
        PageRequest pageable = PageRequest.of(safePage - 1, safePerPage);

        Page<Notification> springPage = (lu != null)
                ? notificationRepository.findByUtilisateurIdAndLuOrderByCreeLeDesc(userId, lu, pageable)
                : notificationRepository.findByUtilisateurIdOrderByCreeLeDesc(userId, pageable);

        return PageResponse.from(springPage.map(NotificationDto::fromEntity));
    }

    @Transactional
    public NotificationDto markAsRead(UUID userId, UUID notificationId) {
        ensureActiveUser(userId);
        Notification notification = notificationRepository.findByIdAndUtilisateurId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "Notification introuvable",
                        "الإشعار غير موجود"
                ));

        if (!notification.isLu()) {
            notification.setLu(true);
            notification.setLuLe(Instant.now());
            notification = notificationRepository.save(notification);
        }
        return NotificationDto.fromEntity(notification);
    }

    @Transactional
    public int markAllAsRead(UUID userId) {
        ensureActiveUser(userId);
        return notificationRepository.markAllAsRead(userId, Instant.now());
    }

    private void ensureActiveUser(UUID userId) {
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
    }
}
