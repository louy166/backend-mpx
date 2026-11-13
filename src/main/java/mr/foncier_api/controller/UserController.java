package mr.foncier_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.foncier_api.dto.billing.InvoiceDto;
import mr.foncier_api.dto.billing.SubscriptionDto;
import mr.foncier_api.dto.common.PageResponse;
import mr.foncier_api.dto.property.PropertySummaryDto;
import mr.foncier_api.dto.unlock.UnlockHistoryItemDto;
import mr.foncier_api.dto.user.DeviceTokenRequestDto;
import mr.foncier_api.dto.user.KycSubmissionResponseDto;
import mr.foncier_api.dto.user.PhotoUploadResponseDto;
import mr.foncier_api.dto.user.UpdateProfileRequestDto;
import mr.foncier_api.dto.user.UpdateRoleRequestDto;
import mr.foncier_api.dto.user.UserDto;
import mr.foncier_api.service.BillingService;
import mr.foncier_api.service.FavoriteService;
import mr.foncier_api.service.PropertyService;
import mr.foncier_api.service.UnlockService;
import mr.foncier_api.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Contrôleur Utilisateurs, Profils, Rôles, KYC, Mes Biens, Favoris, Déverrouillages, Abonnement, Factures & Push Tokens
 * (Contrat API §3, §4, §8, §9 & §10).
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PropertyService propertyService;
    private final FavoriteService favoriteService;
    private final UnlockService unlockService;
    private final BillingService billingService;

    @GetMapping("/me")
    public ResponseEntity<UserDto> getMe(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.getCurrentUserProfile(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<UserDto> updateMe(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequestDto dto
    ) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.updateProfile(userId, dto));
    }

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PhotoUploadResponseDto> uploadPhoto(
            Authentication authentication,
            @RequestParam("photo") MultipartFile photo
    ) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.uploadProfilePhoto(userId, photo));
    }

    @PutMapping("/me/role")
    public ResponseEntity<UserDto> updateRole(
            Authentication authentication,
            @Valid @RequestBody UpdateRoleRequestDto dto
    ) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.updateRole(userId, dto));
    }

    @PostMapping(value = "/me/kyc", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KycSubmissionResponseDto> submitKyc(
            Authentication authentication,
            @RequestParam("nni") String nni,
            @RequestParam("recto") MultipartFile recto,
            @RequestParam("verso") MultipartFile verso,
            @RequestParam("selfie") MultipartFile selfie
    ) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.submitKyc(userId, nni, recto, verso, selfie));
    }

    @PutMapping("/me/device")
    public ResponseEntity<Map<String, String>> registerDeviceToken(
            Authentication authentication,
            @Valid @RequestBody DeviceTokenRequestDto dto
    ) {
        UUID userId = extractUserId(authentication);
        userService.registerDeviceToken(userId, dto);
        return ResponseEntity.ok(Map.of("message", "Device token enregistré"));
    }

    @GetMapping("/me/properties")
    public ResponseEntity<PageResponse<PropertySummaryDto>> listMyProperties(
            Authentication authentication,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage
    ) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(propertyService.listMyProperties(userId, status, page, perPage));
    }

    @PostMapping("/me/favorites/{propertyId}")
    public ResponseEntity<Map<String, Object>> addFavorite(
            Authentication authentication,
            @PathVariable("propertyId") UUID propertyId
    ) {
        UUID userId = extractUserId(authentication);
        favoriteService.addFavorite(userId, propertyId);
        return ResponseEntity.ok(Map.of("message", "Ajouté aux favoris", "est_favori", true));
    }

    @DeleteMapping("/me/favorites/{propertyId}")
    public ResponseEntity<Map<String, Object>> removeFavorite(
            Authentication authentication,
            @PathVariable("propertyId") UUID propertyId
    ) {
        UUID userId = extractUserId(authentication);
        favoriteService.removeFavorite(userId, propertyId);
        return ResponseEntity.ok(Map.of("message", "Retiré des favoris", "est_favori", false));
    }

    @GetMapping("/me/favorites")
    public ResponseEntity<PageResponse<PropertySummaryDto>> listMyFavorites(
            Authentication authentication,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage
    ) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(favoriteService.listMyFavorites(userId, page, perPage));
    }

    @GetMapping("/me/unlocks")
    public ResponseEntity<PageResponse<UnlockHistoryItemDto>> listMyUnlocks(
            Authentication authentication,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage
    ) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(unlockService.listMyUnlocks(userId, page, perPage));
    }

    @GetMapping("/me/subscription")
    public ResponseEntity<SubscriptionDto> getMySubscription(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(billingService.getMyActiveSubscription(userId));
    }

    @GetMapping("/me/invoices")
    public ResponseEntity<PageResponse<InvoiceDto>> listMyInvoices(
            Authentication authentication,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage
    ) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(billingService.listMyInvoices(userId, page, perPage));
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UUID uuid) {
            return uuid;
        }
        return null;
    }
}
