package mr.foncier_api.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import mr.foncier_api.dto.property.PropertySearchCriteria;
import mr.foncier_api.entity.Bien;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implémentation personnalisée combinant filtres dynamiques, recherche plein texte (ILIKE / pg_trgm)
 * et requêtes géospatiales PostGIS (ST_DWithin & ST_Distance sur geography(Point, 4326)).
 */
@Repository
public class BienRepositoryImpl implements BienRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public SearchPageResult searchPublicCatalog(PropertySearchCriteria criteria, boolean featuredOnly) {
        StringBuilder where = new StringBuilder(" WHERE b.est_archive = false AND b.statut = 'publie' ");
        Map<String, Object> params = new LinkedHashMap<>();

        if (featuredOnly) {
            where.append(" AND (b.est_vip = true OR b.est_verifie = true) ");
        }

        if (criteria.type() != null && !criteria.type().isBlank()) {
            where.append(" AND b.type = :type ");
            params.put("type", criteria.type().trim().toLowerCase());
        }

        if (criteria.transaction() != null && !criteria.transaction().isBlank()) {
            where.append(" AND b.transaction = :transaction ");
            params.put("transaction", criteria.transaction().trim().toLowerCase());
        }

        if (criteria.ville() != null && !criteria.ville().isBlank()) {
            where.append(" AND LOWER(b.ville) = LOWER(:ville) ");
            params.put("ville", criteria.ville().trim());
        }

        if (criteria.prixMin() != null) {
            where.append(" AND b.prix >= :prixMin ");
            params.put("prixMin", criteria.prixMin());
        }

        if (criteria.prixMax() != null) {
            where.append(" AND b.prix <= :prixMax ");
            params.put("prixMax", criteria.prixMax());
        }

        if (criteria.surfaceMin() != null) {
            where.append(" AND b.superficie_m2 >= :surfaceMin ");
            params.put("surfaceMin", criteria.surfaceMin());
        }

        if (criteria.chambresMin() != null) {
            where.append(" AND b.nb_chambres >= :chambresMin ");
            params.put("chambresMin", criteria.chambresMin());
        }

        if (criteria.q() != null && !criteria.q().isBlank()) {
            where.append("""
                     AND (
                         b.titre ILIKE :qPattern
                         OR b.description_fr ILIKE :qPattern
                         OR COALESCE(b.description_ar, '') ILIKE :qPattern
                         OR COALESCE(b.quartier_nom, '') ILIKE :qPattern
                         OR COALESCE(b.ville, '') ILIKE :qPattern
                         OR b.reference ILIKE :qPattern
                     )
                    """);
            params.put("qPattern", "%" + criteria.q().trim() + "%");
        }

        boolean hasGeo = criteria.lat() != null && criteria.lng() != null;
        if (hasGeo) {
            double rayonKm = (criteria.rayonKm() != null && criteria.rayonKm() > 0) ? criteria.rayonKm() : 10.0;
            double rayonMeters = rayonKm * 1000.0;
            where.append("""
                     AND b.position_floue IS NOT NULL
                     AND ST_DWithin(
                         b.position_floue,
                         ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                         :rayonMeters
                     )
                    """);
            params.put("lat", criteria.lat());
            params.put("lng", criteria.lng());
            params.put("rayonMeters", rayonMeters);
        }

        // 1. Requête COUNT totale
        String countSql = "SELECT COUNT(*) FROM public.bien b " + where;
        Query countQuery = entityManager.createNativeQuery(countSql);
        params.forEach(countQuery::setParameter);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        if (total == 0) {
            return new SearchPageResult(List.of(), 0L);
        }

        // 2. Expression de distance et clause ORDER BY
        String selectDistance = hasGeo
                ? "ROUND((ST_Distance(b.position_floue, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) / 1000.0)::numeric, 2) AS distance_km"
                : "CAST(NULL AS numeric) AS distance_km";

        String orderBy = resolveOrderBy(criteria.tri(), featuredOnly, hasGeo);

        int page = Math.max(1, criteria.page());
        int perPage = Math.min(100, Math.max(1, criteria.perPage()));
        int offset = (page - 1) * perPage;

        String idsSql = "SELECT b.id, " + selectDistance + " FROM public.bien b " + where + orderBy + " LIMIT :limit OFFSET :offset";
        Query idsQuery = entityManager.createNativeQuery(idsSql);
        params.forEach(idsQuery::setParameter);
        idsQuery.setParameter("limit", perPage);
        idsQuery.setParameter("offset", offset);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = idsQuery.getResultList();
        if (rows.isEmpty()) {
            return new SearchPageResult(List.of(), total);
        }

        List<UUID> orderedIds = new ArrayList<>(rows.size());
        Map<UUID, Double> distanceById = new HashMap<>(rows.size());
        for (Object[] row : rows) {
            UUID id = row[0] instanceof UUID u ? u : UUID.fromString(row[0].toString());
            Double dist = row[1] != null ? ((Number) row[1]).doubleValue() : null;
            orderedIds.add(id);
            distanceById.put(id, dist);
        }

        // 3. Chargement groupé des entités Bien + proprietaire en une seule requête JPQL (zéro N+1)
        List<Bien> loadedBiens = entityManager.createQuery(
                        "SELECT b FROM Bien b JOIN FETCH b.proprietaire WHERE b.id IN :ids",
                        Bien.class
                )
                .setParameter("ids", orderedIds)
                .getResultList();

        Map<UUID, Bien> bienById = new HashMap<>(loadedBiens.size());
        for (Bien b : loadedBiens) {
            bienById.put(b.getId(), b);
        }

        List<BienWithDistance> orderedResult = new ArrayList<>(orderedIds.size());
        for (UUID id : orderedIds) {
            Bien b = bienById.get(id);
            if (b != null) {
                orderedResult.add(new BienWithDistance(b, distanceById.get(id)));
            }
        }

        return new SearchPageResult(orderedResult, total);
    }

    private String resolveOrderBy(String tri, boolean featuredOnly, boolean hasGeo) {
        if (featuredOnly && (tri == null || tri.isBlank())) {
            return " ORDER BY b.est_vip DESC, b.est_verifie DESC, b.vues DESC, b.cree_le DESC ";
        }
        String normalizedTri = tri != null ? tri.trim().toLowerCase() : "";
        return switch (normalizedTri) {
            case "prix_asc" -> " ORDER BY b.prix ASC, b.cree_le DESC ";
            case "prix_desc" -> " ORDER BY b.prix DESC, b.cree_le DESC ";
            case "populaire" -> " ORDER BY b.vues DESC, b.favoris DESC, b.cree_le DESC ";
            case "recent" -> " ORDER BY COALESCE(b.publie_le, b.cree_le) DESC ";
            case "distance_asc" -> hasGeo
                    ? " ORDER BY distance_km ASC, b.cree_le DESC "
                    : " ORDER BY COALESCE(b.publie_le, b.cree_le) DESC ";
            default -> hasGeo
                    ? " ORDER BY distance_km ASC, b.cree_le DESC "
                    : " ORDER BY COALESCE(b.publie_le, b.cree_le) DESC ";
        };
    }
}
