package mr.foncier_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.foncier_api.dto.billing.CreateSubscriptionRequestDto;
import mr.foncier_api.dto.billing.PlanDto;
import mr.foncier_api.dto.billing.SubscriptionDto;
import mr.foncier_api.service.BillingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Contrôleur des Plans d'abonnement et Souscriptions (Contrat API §10).
 */
@RestController
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/plans")
    public ResponseEntity<List<PlanDto>> listPlans() {
        return ResponseEntity.ok(billingService.listPlans());
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<SubscriptionDto> subscribe(
            Authentication authentication,
            @Valid @RequestBody CreateSubscriptionRequestDto dto
    ) {
        UUID userId = extractUserId(authentication);
        SubscriptionDto created = billingService.subscribe(userId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UUID uuid) {
            return uuid;
        }
        return null;
    }
}
