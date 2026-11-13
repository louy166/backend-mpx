package mr.foncier_api.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import mr.foncier_api.dto.user.UserDto;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponseDto(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("user") UserDto user
) {
}
