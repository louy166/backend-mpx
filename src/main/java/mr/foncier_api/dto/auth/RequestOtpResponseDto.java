package mr.foncier_api.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RequestOtpResponseDto(
        @JsonProperty("message") String message,
        @JsonProperty("expires_in") int expiresIn
) {
}
