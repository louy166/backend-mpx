package mr.foncier_api.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import mr.foncier_api.enums.Langue;
import mr.foncier_api.enums.RoleUtilisateur;
import mr.foncier_api.exception.BusinessException;
import mr.foncier_api.exception.ErrorCode;
import mr.foncier_api.service.JwtService;
import mr.foncier_api.dto.auth.AuthResponseDto;
import mr.foncier_api.dto.auth.RefreshTokenRequestDto;
import mr.foncier_api.dto.auth.RequestOtpDto;
import mr.foncier_api.dto.auth.RequestOtpResponseDto;
import mr.foncier_api.dto.auth.VerifyOtpDto;
import mr.foncier_api.entity.Utilisateur;
import mr.foncier_api.repository.UtilisateurRepository;
import mr.foncier_api.dto.user.UserDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final OtpAndTokenStore otpAndTokenStore;
    private final JwtService jwtService;
    private final int otpExpirationSeconds;
    private final long refreshExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UtilisateurRepository utilisateurRepository,
            OtpAndTokenStore otpAndTokenStore,
            JwtService jwtService,
            @Value("${app.otp.expiration-seconds:300}") int otpExpirationSeconds,
            @Value("${app.jwt.refresh-expiration-ms:2592000000}") long refreshExpirationMs
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.otpAndTokenStore = otpAndTokenStore;
        this.jwtService = jwtService;
        this.otpExpirationSeconds = otpExpirationSeconds;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public RequestOtpResponseDto requestOtp(RequestOtpDto dto) {
        String telephone = dto.telephone().trim();
        otpAndTokenStore.checkRateLimitAndRecord(telephone);

        int randomCode = 100000 + secureRandom.nextInt(900000);
        String otpCode = String.valueOf(randomCode);
        otpAndTokenStore.saveOtp(telephone, otpCode);

        log.info("==================================================================");
        log.info("[OTP SMS SIMULATION] Téléphone: {} | Code généré: {} (ou code dev: 123456)", telephone, otpCode);
        log.info("==================================================================");

        return new RequestOtpResponseDto("OTP envoyé", otpExpirationSeconds);
    }

    @Transactional
    public AuthResponseDto verifyOtp(VerifyOtpDto dto) {
        String telephone = dto.telephone().trim();
        boolean valid = otpAndTokenStore.verifyAndConsumeOtp(telephone, dto.otp().trim());

        if (!valid) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    "Code OTP invalide ou expiré",
                    "رمز التحقق غير صالح أو منتهي الصلاحية"
            );
        }

        // Contrat API §2 & Checklist #12 : Si l'utilisateur n'existe pas, le créer automatiquement (role: particulier)
        Utilisateur user = utilisateurRepository.findByTelephone(telephone)
                .orElseGet(() -> {
                    Utilisateur nouveau = Utilisateur.builder()
                            .telephone(telephone)
                            .role(RoleUtilisateur.particulier)
                            .roles(new String[]{"particulier"})
                            .plan("gratuit")
                            .langue(Langue.fr)
                            .kycVerifie(false)
                            .estActif(true)
                            .build();
                    return utilisateurRepository.save(nouveau);
                });

        if (!user.isEstActif()) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Ce compte a été suspendu",
                    "تم تعليق هذا الحساب"
            );
        }

        user.setDernierAcces(Instant.now());
        user = utilisateurRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenId = UUID.randomUUID().toString();
        String refreshToken = jwtService.generateRefreshToken(user, refreshTokenId);
        otpAndTokenStore.saveRefreshToken(refreshTokenId, user.getId(), refreshExpirationMs);

        return new AuthResponseDto(accessToken, refreshToken, UserDto.fromEntity(user));
    }

    @Transactional(readOnly = true)
    public AuthResponseDto refreshToken(RefreshTokenRequestDto dto) {
        String rawToken = dto.refreshToken().trim();
        Claims claims;
        try {
            claims = jwtService.parseAndValidate(rawToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    "Refresh token invalide ou expiré",
                    "رمز التحديث غير صالح أو منتهي الصلاحية"
            );
        }

        if (!"refresh".equals(claims.get("type", String.class)) || claims.getId() == null) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    "Type de jeton invalide",
                    "نوع الرمز غير صالح"
            );
        }

        UUID userId = otpAndTokenStore.consumeRefreshToken(claims.getId());
        if (userId == null) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    "Refresh token déjà utilisé ou révoqué",
                    "تم استخدام رمز التحديث بالفعل أو تم إلغاؤه"
            );
        }

        Utilisateur user = utilisateurRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.UNAUTHORIZED,
                        "Utilisateur introuvable",
                        "المستخدم غير موجود"
                ));

        if (!user.isEstActif()) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "Ce compte a été suspendu",
                    "تم تعليق هذا الحساب"
            );
        }

        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshTokenId = UUID.randomUUID().toString();
        String newRefreshToken = jwtService.generateRefreshToken(user, newRefreshTokenId);
        otpAndTokenStore.saveRefreshToken(newRefreshTokenId, user.getId(), refreshExpirationMs);

        return new AuthResponseDto(newAccessToken, newRefreshToken, null);
    }

    public void logout(RefreshTokenRequestDto dto) {
        if (dto == null || dto.refreshToken() == null) {
            return;
        }
        try {
            Claims claims = jwtService.parseAndValidate(dto.refreshToken().trim());
            otpAndTokenStore.revokeRefreshToken(claims.getId());
        } catch (JwtException | IllegalArgumentException ignored) {
        }
    }
}
