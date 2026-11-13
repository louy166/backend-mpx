package mr.foncier_api.repository;

import mr.foncier_api.dto.property.PropertySearchCriteria;
import mr.foncier_api.entity.Bien;

import java.util.List;

public interface BienRepositoryCustom {

    record BienWithDistance(Bien bien, Double distanceKm) {
    }

    record SearchPageResult(List<BienWithDistance> items, long total) {
    }

    SearchPageResult searchPublicCatalog(PropertySearchCriteria criteria, boolean featuredOnly);
}
