package mr.foncier_api.dto.user;

import mr.foncier_api.enums.Langue;

import com.fasterxml.jackson.annotation.JsonProperty;
import mr.foncier_api.entity.Utilisateur;

import java.time.Instant;
import java.util.UUID;

/**
 * Objet User complet retourné par POST /auth/verify-otp et GET /users/me (Contrat API §2 & §3).
 */
public record UserDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("telephone") String telephone,
        @JsonProperty("nom") String nom,
        @JsonProperty("prenom") String prenom,
        @JsonProperty("email") String email,
        @JsonProperty("role") String role,
        @JsonProperty("photo_url") String photoUrl,
        @JsonProperty("nni") String nni,
        @JsonProperty("kyc_verifie") boolean kycVerifie,
        @JsonProperty("date_inscription") Instant dateInscription,
        @JsonProperty("derniere_connexion") Instant derniereConnexion,
        @JsonProperty("plan") String plan,
        @JsonProperty("langue") String langue
) {
    public static UserDto fromEntity(Utilisateur u) {
        return new UserDto(
                u.getId(),
                u.getTelephone(),
                u.getNom(),
                u.getPrenom(),
                u.getEmail(),
                u.getRole() != null ? u.getRole().name() : "particulier",
                u.getPhotoUrl(),
                u.getNni(),
                u.isKycVerifie(),
                u.getCreeLe(),
                u.getDernierAcces(),
                u.getPlan(),
                u.getLangue() != null ? u.getLangue().name() : "fr"
        );
    }
}
