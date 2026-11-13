package mr.foncier_api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.foncier_api.dto.common.PageResponse;
import mr.foncier_api.dto.property.CreatePropertyRequestDto;
import mr.foncier_api.dto.property.PropertyDetailDto;
import mr.foncier_api.dto.property.PropertyDocumentDto;
import mr.foncier_api.dto.property.PropertyPhotosResponseDto;
import mr.foncier_api.dto.property.PropertySearchCriteria;
import mr.foncier_api.dto.property.PropertySummaryDto;
import mr.foncier_api.dto.property.UpdatePropertyRequestDto;
import mr.foncier_api.dto.property.UpdatePropertyStatusRequestDto;
import mr.foncier_api.dto.unlock.UnlockResponseDto;
import mr.foncier_api.service.PropertyService;
import mr.foncier_api.service.UnlockService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Contrôleur des Biens immobiliers, Documents fonciers, Déverrouillages & Recherche géospatiale PostGIS
 * (Contrat API §4, §5 & §8).
 */
@RestController
@RequestMapping("/properties")
@RequiredArgsConstructor
public class PropertyController {

    private final PropertyService propertyService;
    private final UnlockService unlockService;

    @GetMapping
    public ResponseEntity<PageResponse<PropertySummaryDto>> listProperties(
            Authentication authentication,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "transaction", required = false) String transaction,
            @RequestParam(name = "ville", required = false) String ville,
            @RequestParam(name = "prix_min", required = false) BigDecimal prixMin,
            @RequestParam(name = "prix_max", required = false) BigDecimal prixMax,
            @RequestParam(name = "surface_min", required = false) BigDecimal surfaceMin,
            @RequestParam(name = "chambres_min", required = false) Integer chambresMin,
            @RequestParam(name = "lat", required = false) Double lat,
            @RequestParam(name = "lng", required = false) Double lng,
            @RequestParam(name = "rayon_km", required = false) Double rayonKm,
            @RequestParam(name = "tri", required = false) String tri,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage
    ) {
        PropertySearchCriteria criteria = new PropertySearchCriteria(
                type,
                transaction,
                ville,
                prixMin,
                prixMax,
                surfaceMin,
                chambresMin,
                lat,
                lng,
                rayonKm,
                tri,
                q,
                page,
                perPage
        );
        return ResponseEntity.ok(
                propertyService.searchPublicCatalog(criteria, extractUserId(authentication), false)
        );
    }

    @GetMapping("/featured")
    public ResponseEntity<PageResponse<PropertySummaryDto>> listFeaturedProperties(
            Authentication authentication,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage
    ) {
        PropertySearchCriteria criteria = new PropertySearchCriteria(
                null, null, null, null, null, null, null,
                null, null, null, null, null,
                page, perPage
        );
        return ResponseEntity.ok(
                propertyService.searchPublicCatalog(criteria, extractUserId(authentication), true)
        );
    }

    @GetMapping("/nearby")
    public ResponseEntity<PageResponse<PropertySummaryDto>> listNearbyProperties(
            Authentication authentication,
            @RequestParam(name = "lat") Double lat,
            @RequestParam(name = "lng") Double lng,
            @RequestParam(name = "rayon_km", defaultValue = "10") Double rayonKm,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "transaction", required = false) String transaction,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage
    ) {
        PropertySearchCriteria criteria = new PropertySearchCriteria(
                type,
                transaction,
                null,
                null,
                null,
                null,
                null,
                lat,
                lng,
                rayonKm,
                "distance_asc",
                null,
                page,
                perPage
        );
        return ResponseEntity.ok(
                propertyService.searchPublicCatalog(criteria, extractUserId(authentication), false)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<PropertyDetailDto> getPropertyDetail(
            Authentication authentication,
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(
                propertyService.getPropertyDetail(id, extractUserId(authentication), extractRole(authentication))
        );
    }

    @PostMapping
    public ResponseEntity<PropertyDetailDto> createProperty(
            Authentication authentication,
            @Valid @RequestBody CreatePropertyRequestDto dto
    ) {
        PropertyDetailDto created = propertyService.createProperty(
                extractUserId(authentication),
                extractRole(authentication),
                dto
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PropertyDetailDto> updateProperty(
            Authentication authentication,
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdatePropertyRequestDto dto
    ) {
        return ResponseEntity.ok(
                propertyService.updateProperty(id, extractUserId(authentication), extractRole(authentication), dto)
        );
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PropertyDocumentDto> uploadPropertyDocument(
            Authentication authentication,
            @PathVariable("id") UUID id,
            @RequestParam("type") String type,
            @RequestParam("file") MultipartFile file
    ) {
        PropertyDocumentDto response = propertyService.uploadPropertyDocument(
                id,
                extractUserId(authentication),
                extractRole(authentication),
                type,
                file
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PropertyPhotosResponseDto> uploadPropertyPhotos(
            Authentication authentication,
            @PathVariable("id") UUID id,
            @RequestParam("photos") MultipartFile[] photos
    ) {
        PropertyPhotosResponseDto response = propertyService.uploadPropertyPhotos(
                id,
                extractUserId(authentication),
                extractRole(authentication),
                photos
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<UnlockResponseDto> unlockProperty(
            Authentication authentication,
            @PathVariable("id") UUID id,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(
                unlockService.unlockProperty(id, extractUserId(authentication), request)
        );
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PropertyDetailDto> updatePropertyStatus(
            Authentication authentication,
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdatePropertyStatusRequestDto dto
    ) {
        return ResponseEntity.ok(
                propertyService.updatePropertyStatus(
                        id,
                        extractUserId(authentication),
                        extractRole(authentication),
                        dto
                )
        );
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UUID uuid) {
            return uuid;
        }
        return null;
    }

    private String extractRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return null;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String auth = authority.getAuthority();
            if (auth != null && auth.startsWith("ROLE_")) {
                return auth.substring(5).toLowerCase();
            }
        }
        return null;
    }
}
