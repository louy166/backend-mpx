package mr.foncier_api.dto.property;

import java.math.BigDecimal;

/**
 * Critères de recherche multicritère et géospatiale PostGIS (GET /v1/properties & /nearby — Contrat API §4).
 */
public record PropertySearchCriteria(
        String type,
        String transaction,
        String ville,
        BigDecimal prixMin,
        BigDecimal prixMax,
        BigDecimal surfaceMin,
        Integer chambresMin,
        Double lat,
        Double lng,
        Double rayonKm,
        String tri,
        String q,
        int page,
        int perPage
) {
}
