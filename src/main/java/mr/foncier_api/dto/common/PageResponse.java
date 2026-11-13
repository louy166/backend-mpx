package mr.foncier_api.dto.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Enveloppe de pagination standard exigée par le Contrat API Frontend (§1 & Checklist #4).
 */
public record PageResponse<T>(
        @JsonProperty("data") List<T> data,
        @JsonProperty("total") long total,
        @JsonProperty("page") int page,
        @JsonProperty("per_page") int perPage,
        @JsonProperty("total_pages") int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> springPage) {
        return new PageResponse<>(
                springPage.getContent(),
                springPage.getTotalElements(),
                springPage.getNumber() + 1,
                springPage.getSize(),
                springPage.getTotalPages()
        );
    }
}
