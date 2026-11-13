package mr.foncier_api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.foncier_api.entity.FormuleAbonnement;
import mr.foncier_api.repository.FormuleAbonnementRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Initialise de façon idempotente les 4 formules d'abonnement de référence du Contrat API (§10 & §11).
 * S'exécute dans tous les environnements avant DevDataSeeder (@Order(1)).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanInitializer {

    private final FormuleAbonnementRepository formuleAbonnementRepository;

    @Order(1)
    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void ensureReferencePlansExist() {
        upsertPlan("gratuit", "Gratuit", "مجاني", new BigDecimal("0.00"), 2, 0);
        upsertPlan("basic", "Starter", "ستارتر", new BigDecimal("5000.00"), 10, 5);
        upsertPlan("premium", "Pro", "برو", new BigDecimal("15000.00"), 50, 30);
        upsertPlan("vip", "VIP", "في آي بي", new BigDecimal("50000.00"), -1, -1);
    }

    private void upsertPlan(
            String code,
            String nomFr,
            String nomAr,
            BigDecimal tarifAnnuel,
            int limiteAnnonces,
            int deverrouillagesInclus
    ) {
        FormuleAbonnement formule = formuleAbonnementRepository.findByCodeIgnoreCase(code)
                .orElseGet(() -> FormuleAbonnement.builder()
                        .code(code)
                        .build());

        formule.setCode(code);
        formule.setNomFr(nomFr);
        formule.setNomAr(nomAr);
        formule.setTarifAnnuel(tarifAnnuel);
        formule.setLimiteAnnonces(limiteAnnonces);
        formule.setDeverrouillagesInclus(deverrouillagesInclus);
        formule.setEstActif(true);
        if (formule.getFonctionnalitesIncluses() == null) {
            formule.setFonctionnalitesIncluses(Map.of());
        }
        formuleAbonnementRepository.save(formule);
    }
}
