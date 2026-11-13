package mr.foncier_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration Web MVC permettant de servir localement les médias publics (/uploads/users/**, /uploads/properties/**).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadDir;

    public WebConfig(@Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        String locationUri = uploadPath.toUri().toString();
        if (!locationUri.endsWith("/")) {
            locationUri += "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(locationUri);
    }
}
