package mr.foncier_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.foncier_api.dto.common.PageResponse;
import mr.foncier_api.dto.mandat.CreateMandatRequestDto;
import mr.foncier_api.dto.mandat.MandatDto;
import mr.foncier_api.dto.mandat.UpdateMandatStatusRequestDto;
import mr.foncier_api.service.MandatService;
import org.springframework.http.HttpStatus;
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

import java.util.UUID;

/**
 * Contrôleur des Mandats immobiliers (Contrat API §6).
 */
@RestController
@RequestMapping("/mandats")
@RequiredArgsConstructor
public class MandatController {

    private final MandatService mandatService;

    @GetMapping
    public ResponseEntity<PageResponse<MandatDto>> listMandats(
            Authentication authentication,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage
    ) {
        return ResponseEntity.ok(
                mandatService.listMandats(
                        extractUserId(authentication),
                        extractRole(authentication),
                        status,
                        page,
                        perPage
                )
        );
    }

    @PostMapping
    public ResponseEntity<MandatDto> createMandat(
            Authentication authentication,
            @Valid @RequestBody CreateMandatRequestDto dto
    ) {
        MandatDto created = mandatService.createMandat(
                extractUserId(authentication),
                extractRole(authentication),
                dto
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<MandatDto> updateMandatStatus(
            Authentication authentication,
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateMandatStatusRequestDto dto
    ) {
        return ResponseEntity.ok(
                mandatService.updateMandatStatus(
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
