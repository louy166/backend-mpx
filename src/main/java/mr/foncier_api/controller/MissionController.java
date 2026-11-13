package mr.foncier_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.foncier_api.dto.common.PageResponse;
import mr.foncier_api.dto.mission.CreateMissionRequestDto;
import mr.foncier_api.dto.mission.MissionDto;
import mr.foncier_api.dto.mission.MissionPhotosResponseDto;
import mr.foncier_api.dto.mission.UpdateMissionRequestDto;
import mr.foncier_api.service.MissionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Contrôleur des Missions et Opérations terrain (Contrat API §7).
 */
@RestController
@RequestMapping("/missions")
@RequiredArgsConstructor
public class MissionController {

    private final MissionService missionService;

    @GetMapping
    public ResponseEntity<PageResponse<MissionDto>> listMissions(
            Authentication authentication,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage
    ) {
        return ResponseEntity.ok(
                missionService.listMissions(
                        extractUserId(authentication),
                        extractRole(authentication),
                        status,
                        page,
                        perPage
                )
        );
    }

    @PostMapping
    public ResponseEntity<MissionDto> createMission(
            Authentication authentication,
            @Valid @RequestBody CreateMissionRequestDto dto
    ) {
        MissionDto created = missionService.createMission(
                extractUserId(authentication),
                extractRole(authentication),
                dto
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MissionDto> getMissionDetail(
            Authentication authentication,
            @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(
                missionService.getMissionDetail(
                        id,
                        extractUserId(authentication),
                        extractRole(authentication)
                )
        );
    }

    @PatchMapping("/{id}")
    public ResponseEntity<MissionDto> updateMission(
            Authentication authentication,
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateMissionRequestDto dto
    ) {
        return ResponseEntity.ok(
                missionService.updateMission(
                        id,
                        extractUserId(authentication),
                        extractRole(authentication),
                        dto
                )
        );
    }

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MissionPhotosResponseDto> uploadMissionPhotos(
            Authentication authentication,
            @PathVariable("id") UUID id,
            @RequestParam("photos") MultipartFile[] photos
    ) {
        MissionPhotosResponseDto response = missionService.uploadMissionPhotos(
                id,
                extractUserId(authentication),
                extractRole(authentication),
                photos
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
