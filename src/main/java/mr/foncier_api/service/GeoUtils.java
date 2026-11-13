package mr.foncier_api.service;

import mr.foncier_api.dto.common.GeoPointDto;
import mr.foncier_api.exception.BusinessException;
import mr.foncier_api.exception.ErrorCode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

/**
 * Utilitaires géospatiaux JTS / PostGIS SRID 4326 et algorithme de floutage anti-contournement (EX-030, EX-031).
 */
public final class GeoUtils {

    public static final int SRID_WGS84 = 4326;
    private static final double METERS_PER_DEGREE_LAT = 111_320.0;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), SRID_WGS84);
    private static final SecureRandom RANDOM = new SecureRandom();

    private GeoUtils() {
    }

    public static void validateCoordinates(double lat, double lng) {
        if (Double.isNaN(lat) || Double.isInfinite(lat) || lat < -90.0 || lat > 90.0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Latitude invalide (attendu entre -90 et 90)",
                    "خط العرض غير صالح (يجب أن يكون بين -90 و 90)",
                    Map.of("latitude", List.of("Doit être compris entre -90 et 90"))
            );
        }
        if (Double.isNaN(lng) || Double.isInfinite(lng) || lng < -180.0 || lng > 180.0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "Longitude invalide (attendu entre -180 et 180)",
                    "خط الطول غير صالح (يجب أن يكون بين -180 و 180)",
                    Map.of("longitude", List.of("Doit être compris entre -180 et 180"))
            );
        }
    }

    public static Point toPoint(GeoPointDto dto) {
        if (dto == null) {
            return null;
        }
        validateCoordinates(dto.latitude(), dto.longitude());
        return createPoint(dto.latitude(), dto.longitude());
    }

    /**
     * Attention : dans JTS / PostGIS (X, Y) = (longitude, latitude).
     */
    public static Point createPoint(double latitude, double longitude) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(SRID_WGS84);
        return point;
    }

    /**
     * Génère un point décalé aléatoirement entre ~35% et 100% du rayon de floutage (EX-031).
     */
    public static Point generateFuzzyPoint(Point exactPoint, int maxRadiusMeters) {
        if (exactPoint == null) {
            return null;
        }
        int effectiveMax = Math.max(50, maxRadiusMeters);
        double minRadius = Math.max(30.0, effectiveMax * 0.35);
        double distanceMeters = minRadius + (RANDOM.nextDouble() * (effectiveMax - minRadius));
        double angleRadians = RANDOM.nextDouble() * 2.0 * Math.PI;

        double lat = exactPoint.getY();
        double lng = exactPoint.getX();

        double deltaLat = (distanceMeters * Math.cos(angleRadians)) / METERS_PER_DEGREE_LAT;
        double cosLat = Math.max(0.01, Math.cos(Math.toRadians(lat)));
        double deltaLng = (distanceMeters * Math.sin(angleRadians)) / (METERS_PER_DEGREE_LAT * cosLat);

        double fuzzyLat = Math.max(-89.9999, Math.min(89.9999, lat + deltaLat));
        double fuzzyLng = Math.max(-179.9999, Math.min(179.9999, lng + deltaLng));

        return createPoint(fuzzyLat, fuzzyLng);
    }
}
