package mr.foncier_api.dto.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.locationtech.jts.geom.Point;

/**
 * Format JSON normalisé des coordonnées géographiques (Contrat API §1).
 */
public record GeoPointDto(
        @JsonProperty("latitude") double latitude,
        @JsonProperty("longitude") double longitude
) {
    public static GeoPointDto fromPoint(Point point) {
        if (point == null) {
            return null;
        }
        return new GeoPointDto(point.getY(), point.getX());
    }
}
