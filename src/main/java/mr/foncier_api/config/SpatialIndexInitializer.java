package mr.foncier_api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Initialise les index spatiaux PostGIS (GIST) et plein texte (GIN pg_trgm)
 * après la création/mise à jour du schéma JPA par Hibernate (Checklist #7).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpatialIndexInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Order(1)
    @EventListener(ApplicationReadyEvent.class)
    public void ensureSpatialAndTrgmIndexes() {
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_bien_position_floue_gist ON public.bien USING GIST (position_floue)"
        );
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_bien_position_exacte_gist ON public.bien USING GIST (position_exacte)"
        );
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_bien_titre_trgm ON public.bien USING GIN (titre gin_trgm_ops)"
        );
        log.info("Index PostGIS GIST (position_floue, position_exacte) et GIN pg_trgm vérifiés sur public.bien.");
    }
}
