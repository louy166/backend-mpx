package mr.foncier_api.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpDto(
        @NotBlank(message = "Le champ telephone est requis")
        @Pattern(regexp = "^\\+222\\d{8}$", message = "Format invalide, attendu: +222XXXXXXXX")
        @JsonProperty("telephone")
        String telephone,

        @NotBlank(message = "Le code OTP est requis")
        @Pattern(regexp = "^\\d{6}$", message = "Le code OTP doit contenir 6 chiffres")
        @JsonProperty("otp")
        String otp
) {
}
