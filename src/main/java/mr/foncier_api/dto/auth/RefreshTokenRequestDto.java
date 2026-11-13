package mr.foncier_api.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequestDto(
        @NotBlank(message = "Le champ refresh_token est requis")
        @JsonProperty("refresh_token")
        String refreshToken
) {
}
