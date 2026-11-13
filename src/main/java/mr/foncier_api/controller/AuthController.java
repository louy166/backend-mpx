package mr.foncier_api.controller;

import mr.foncier_api.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mr.foncier_api.dto.auth.AuthResponseDto;
import mr.foncier_api.dto.auth.RefreshTokenRequestDto;
import mr.foncier_api.dto.auth.RequestOtpDto;
import mr.foncier_api.dto.auth.RequestOtpResponseDto;
import mr.foncier_api.dto.auth.VerifyOtpDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Contrôleur d'Authentification OTP (Contrat API §2).
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/request-otp")
    public ResponseEntity<RequestOtpResponseDto> requestOtp(@Valid @RequestBody RequestOtpDto dto) {
        return ResponseEntity.ok(authService.requestOtp(dto));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponseDto> verifyOtp(@Valid @RequestBody VerifyOtpDto dto) {
        return ResponseEntity.ok(authService.verifyOtp(dto));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto dto) {
        return ResponseEntity.ok(authService.refreshToken(dto));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody(required = false) RefreshTokenRequestDto dto) {
        authService.logout(dto);
        return ResponseEntity.ok(Map.of("message", "Session fermée"));
    }
}
