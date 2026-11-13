package mr.foncier_api.controller;

import lombok.RequiredArgsConstructor;
import mr.foncier_api.dto.common.PageResponse;
import mr.foncier_api.dto.notification.NotificationDto;
import mr.foncier_api.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Contrôleur des Notifications in-app (Contrat API §9).
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<PageResponse<NotificationDto>> listMyNotifications(
            Authentication authentication,
            @RequestParam(name = "lu", required = false) Boolean lu,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage
    ) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(notificationService.listMyNotifications(userId, lu, page, perPage));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationDto> markAsRead(
            Authentication authentication,
            @PathVariable("id") UUID id
    ) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(notificationService.markAsRead(userId, id));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        int updated = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of(
                "message", "Toutes les notifications ont été marquées comme lues",
                "updated_count", updated
        ));
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UUID uuid) {
            return uuid;
        }
        return null;
    }
}
