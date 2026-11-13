package mr.foncier_api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mr.foncier_api.entity.Document;
import mr.foncier_api.entity.Utilisateur;
import mr.foncier_api.enums.StatutKyc;
import mr.foncier_api.repository.DocumentRepository;
import mr.foncier_api.repository.UtilisateurRepository;
import mr.foncier_api.service.OtpAndTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class UserIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OtpAndTokenStore otpAndTokenStore;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private DocumentRepository documentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private String accessToken;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        otpAndTokenStore.clearAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        String phone = "+22239990010";
        MvcResult verifyResult = mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telephone\": \"" + phone + "\", \"otp\": \"123456\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
        accessToken = json.get("access_token").asText();
        userId = UUID.fromString(json.get("user").get("id").asText());
    }

    @Test
    @DisplayName("V2 : GET /users/me sans token (401) et avec token JWT valide (200)")
    void verifierGetMe() throws Exception {
        // 1. Sans token -> 401 UNAUTHORIZED
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // 2. Avec token -> 200 OK + profil complet
        mockMvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.telephone").value("+22239990010"))
                .andExpect(jsonPath("$.role").value("particulier"))
                .andExpect(jsonPath("$.plan").value("gratuit"))
                .andExpect(jsonPath("$.kyc_verifie").value(false))
                .andExpect(jsonPath("$.langue").value("fr"));
    }

    @Test
    @DisplayName("V2 : PUT /users/me mise à jour partielle et détection de doublon email (409 CONFLICT)")
    void verifierUpdateProfileEtDoublonEmail() throws Exception {
        // 1. Mise à jour du profil
        mockMvc.perform(put("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nom": "Diallo",
                                  "prenom": "Aminata",
                                  "email": "aminata.test@mail.mr",
                                  "langue": "ar"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Diallo"))
                .andExpect(jsonPath("$.prenom").value("Aminata"))
                .andExpect(jsonPath("$.email").value("aminata.test@mail.mr"))
                .andExpect(jsonPath("$.langue").value("ar"));

        // 2. Création d'un autre utilisateur ayant déjà un email
        Utilisateur autre = Utilisateur.builder()
                .telephone("+22239990011")
                .email("deja.pris@mail.mr")
                .build();
        utilisateurRepository.saveAndFlush(autre);

        // 3. Tentative d'utiliser l'email déjà pris -> 409 CONFLICT
        mockMvc.perform(put("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"deja.pris@mail.mr\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("V2 : PUT /users/me/role changement de rôle valide, blocage admin (403) et rôle inconnu (400)")
    void verifierUpdateRoleEtSecuriteAdmin() throws Exception {
        // 1. Passage valide à 'proprietaire'
        mockMvc.perform(put("/users/me/role")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"proprietaire\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("proprietaire"));

        // 2. Tentative de passage à 'admin' -> 403 FORBIDDEN (Contrat API §3)
        mockMvc.perform(put("/users/me/role")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"admin\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 3. Rôle inexistant -> 400 VALIDATION_ERROR
        mockMvc.perform(put("/users/me/role")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"super_hacker\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.role").isArray());
    }

    @Test
    @DisplayName("V2 : POST /users/me/photo upload multipart valide (200) et rejet MIME invalide (400)")
    void verifierUploadPhotoProfil() throws Exception {
        MockMultipartFile validPhoto = new MockMultipartFile(
                "photo",
                "avatar.jpg",
                "image/jpeg",
                "fake-jpeg-binary-content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/users/me/photo")
                        .file(validPhoto)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photo_url").isNotEmpty());

        MockMultipartFile invalidFile = new MockMultipartFile(
                "photo",
                "script.exe",
                "application/octet-stream",
                "malicious-binary".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/users/me/photo")
                        .file(invalidFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.photo").isArray());
    }

    @Test
    @DisplayName("V2 : POST /users/me/kyc soumission complète NNI + 3 fichiers et traçabilité SHA-256")
    void verifierSoumissionKyc() throws Exception {
        MockMultipartFile recto = new MockMultipartFile(
                "recto",
                "cni-recto.jpg",
                "image/jpeg",
                "contenu-recto-image".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile verso = new MockMultipartFile(
                "verso",
                "cni-verso.png",
                "image/png",
                "contenu-verso-image".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile selfie = new MockMultipartFile(
                "selfie",
                "selfie.jpg",
                "image/jpeg",
                "contenu-selfie-image".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/users/me/kyc")
                        .file(recto)
                        .file(verso)
                        .file(selfie)
                        .param("nni", "1234567890")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kyc_verifie").value(false))
                .andExpect(jsonPath("$.message").value("Documents soumis, verification en cours"));

        Utilisateur updatedUser = utilisateurRepository.findById(userId).orElseThrow();
        assertEquals("1234567890", updatedUser.getNni());
        assertEquals(StatutKyc.en_cours, updatedUser.getStatutKyc());
        assertFalse(updatedUser.isKycVerifie());
        assertNotNull(updatedUser.getKycRectoUrl());
        assertNotNull(updatedUser.getKycVersoUrl());
        assertNotNull(updatedUser.getKycSelfieUrl());

        List<Document> docs = documentRepository.findByUtilisateurId(userId);
        assertEquals(3, docs.size(), "3 documents KYC (recto, verso, selfie) doivent être enregistrés dans public.document");
        assertTrue(docs.stream().allMatch(d -> d.getEmpreinteSha256() != null && d.getEmpreinteSha256().length() == 64));
    }

    @Test
    @DisplayName("V2 : PUT /users/me/device enregistrement sans doublon du token push FCM/APNs")
    void verifierEnregistrementDeviceToken() throws Exception {
        mockMvc.perform(put("/users/me/device")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"device_token\": \"fcm-token-abc-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Device token enregistré"));

        // Deuxième appel avec le même token -> pas de doublon
        mockMvc.perform(put("/users/me/device")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"device_token\": \"fcm-token-abc-123\"}"))
                .andExpect(status().isOk());

        Utilisateur updatedUser = utilisateurRepository.findById(userId).orElseThrow();
        assertEquals(1, updatedUser.getDeviceTokens().length);
        assertEquals("fcm-token-abc-123", updatedUser.getDeviceTokens()[0]);
    }
}
