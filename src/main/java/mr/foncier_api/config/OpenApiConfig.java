package mr.foncier_api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration OpenAPI 3.1 / Swagger UI exposée sur /api/docs (Contrat API §13 Checklist #14).
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI foncierMpxOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Foncier Mauritanie (MPX) — API Backend")
                        .version("v1")
                        .description("""
                                Contrat API Backend officiel pour les applications Mobile (React Native) et Web Admin (Angular) de Foncier Mauritanie (MPX).
                                - Authentification stateless JWT + SMS OTP (`/auth/*`)
                                - Géolocalisation PostGIS `geography(Point, 4326)` & floutage anti-contournement (`N3`)
                                - Tous les payloads JSON utilisent strictement `snake_case` et des dates ISO-8601 UTC.
                                """))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Saisir uniquement l'`access_token` JWT obtenu via `POST /auth/verify-otp`")));
    }
}
