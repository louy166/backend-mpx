package mr.foncier_api;

import mr.foncier_api.entity.Utilisateur;
import mr.foncier_api.enums.Langue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mr.foncier_api.service.OtpAndTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class AuthIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OtpAndTokenStore otpAndTokenStore;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        otpAndTokenStore.clearAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("V0 & V1 : Validation du numéro de téléphone mauritanien et format d'erreur VALIDATION_ERROR")
    void verifierValidationFormatTelephone() throws Exception {
        mockMvc.perform(post("/auth/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telephone\": \"06123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.telephone[0]").value("Format invalide, attendu: +222XXXXXXXX"));
    }

    @Test
    @DisplayName("V1 : Rate limiting sur /auth/request-otp (max 3 par minute par numéro)")
    void verifierRateLimitingOtp() throws Exception {
        String payload = "{\"telephone\": \"+22237998877\"}";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/request-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("OTP envoyé"))
                    .andExpect(jsonPath("$.expires_in").value(300));
        }

        // 4e appel dans la même minute -> 429 RATE_LIMITED + Retry-After
        mockMvc.perform(post("/auth/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName("V1 : Parcours complet OTP -> Auto-création Utilisateur -> Rotation du Refresh Token")
    void verifierParcoursCompletOtpEtRefresh() throws Exception {
        String testPhone = "+22239990002";
        // 1. Demande d'OTP
        mockMvc.perform(post("/auth/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telephone\": \"" + testPhone + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expires_in").value(300));

        // 2. Vérification de l'OTP (auto-création de l'utilisateur)
        MvcResult verifyResult = mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telephone\": \"" + testPhone + "\", \"otp\": \"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.user.telephone").value(testPhone))
                .andExpect(jsonPath("$.user.role").value("particulier"))
                .andExpect(jsonPath("$.user.plan").value("gratuit"))
                .andExpect(jsonPath("$.user.kyc_verifie").value(false))
                .andExpect(jsonPath("$.user.langue").value("fr"))
                .andReturn();

        JsonNode verifyJson = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
        String firstRefreshToken = verifyJson.get("refresh_token").asText();

        // 3. Rotation du Refresh Token
        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\": \"" + firstRefreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andReturn();

        // 4. Réutilisation de l'ancien refresh_token doit échouer en 401 UNAUTHORIZED
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\": \"" + firstRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
