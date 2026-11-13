package mr.foncier_api.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Réponse de soumission des documents KYC (POST /v1/users/me/kyc — Contrat API §3).
 */
public record KycSubmissionResponseDto(
        @JsonProperty("kyc_verifie")
        boolean kycVerifie,

        @JsonProperty("message")
        String message
) {
}
