package mr.foncier_api.service;

import mr.foncier_api.exception.BusinessException;
import mr.foncier_api.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestion thread-safe des OTP, du Rate Limiting (max 3/min par numéro — Checklist #9)
 * et de la rotation stricte des Refresh Tokens (Checklist #10).
 */
@Component
public class OtpAndTokenStore {

    private record OtpEntry(String code, Instant expiresAt) {}
    private record RefreshEntry(UUID userId, Instant expiresAt) {}

    private final Map<String, OtpEntry> otpMap = new ConcurrentHashMap<>();
    private final Map<String, List<Instant>> rateLimitMap = new ConcurrentHashMap<>();
    private final Map<String, RefreshEntry> refreshTokenMap = new ConcurrentHashMap<>();

    private final int otpExpirationSeconds;
    private final int maxRequestsPerMinute;
    private final boolean devMode;
    private final String devCode;

    public OtpAndTokenStore(
            @Value("${app.otp.expiration-seconds:300}") int otpExpirationSeconds,
            @Value("${app.otp.max-requests-per-minute:3}") int maxRequestsPerMinute,
            @Value("${app.otp.dev-mode:true}") boolean devMode,
            @Value("${app.otp.dev-code:123456}") String devCode
    ) {
        this.otpExpirationSeconds = otpExpirationSeconds;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.devMode = devMode;
        this.devCode = devCode;
    }

    public void checkRateLimitAndRecord(String telephone) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(60);

        rateLimitMap.compute(telephone, (key, timestamps) -> {
            List<Instant> list = timestamps == null ? new ArrayList<>() : new ArrayList<>(timestamps);
            list.removeIf(t -> t.isBefore(windowStart));

            if (list.size() >= maxRequestsPerMinute) {
                long retryAfter = Math.max(1, 60 - (now.getEpochSecond() - list.get(0).getEpochSecond()));
                throw new BusinessException(
                        ErrorCode.RATE_LIMITED,
                        "Trop de demandes de code OTP. Veuillez réessayer dans " + retryAfter + " secondes.",
                        "طلبات كثيرة جدًا للحصول على الرمز. يرجى المحاولة مرة أخرى بعد " + retryAfter + " ثانية.",
                        null,
                        (int) retryAfter
                );
            }
            list.add(now);
            return list;
        });
    }

    public void saveOtp(String telephone, String code) {
        otpMap.put(telephone, new OtpEntry(code, Instant.now().plusSeconds(otpExpirationSeconds)));
    }

    public boolean verifyAndConsumeOtp(String telephone, String submittedOtp) {
        OtpEntry entry = otpMap.get(telephone);
        Instant now = Instant.now();

        if (entry != null && now.isBefore(entry.expiresAt())) {
            if (entry.code().equals(submittedOtp) || (devMode && devCode.equals(submittedOtp))) {
                otpMap.remove(telephone);
                return true;
            }
        } else if (devMode && devCode.equals(submittedOtp)) {
            otpMap.remove(telephone);
            return true;
        }
        return false;
    }

    public void saveRefreshToken(String tokenId, UUID userId, long expirationMs) {
        refreshTokenMap.put(tokenId, new RefreshEntry(userId, Instant.now().plusMillis(expirationMs)));
    }

    /**
     * Rotation atomique : consomme et supprime le refresh token dès son utilisation.
     */
    public UUID consumeRefreshToken(String tokenId) {
        RefreshEntry entry = refreshTokenMap.remove(tokenId);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            return null;
        }
        return entry.userId();
    }

    public void revokeRefreshToken(String tokenId) {
        if (tokenId != null) {
            refreshTokenMap.remove(tokenId);
        }
    }

    public void clearAll() {
        otpMap.clear();
        rateLimitMap.clear();
        refreshTokenMap.clear();
    }
}
