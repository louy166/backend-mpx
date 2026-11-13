package mr.foncier_api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mr.foncier_api.entity.Bien;
import mr.foncier_api.entity.Utilisateur;
import mr.foncier_api.enums.RoleUtilisateur;
import mr.foncier_api.enums.StatutBien;
import mr.foncier_api.repository.BienRepository;
import mr.foncier_api.repository.UtilisateurRepository;
import mr.foncier_api.service.JwtService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class Vague5IntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OtpAndTokenStore otpAndTokenStore;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private BienRepository bienRepository;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    private Utilisateur owner;
    private Utilisateur fieldAgent;
    private Utilisateur outsider;
    private Utilisateur admin;

    private String ownerToken;
    private String fieldAgentToken;
    private String outsiderToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        otpAndTokenStore.clearAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        owner = Utilisateur.builder()
                .telephone("+22239950001")
                .nom("Ould Mohamed")
                .prenom("Cheikh")
                .email("cheikh.owner@mail.mr")
                .role(RoleUtilisateur.proprietaire)
                .roles(new String[]{"proprietaire"})
                .plan("premium")
                .build();
        owner = utilisateurRepository.saveAndFlush(owner);
        ownerToken = jwtService.generateAccessToken(owner);

        fieldAgent = Utilisateur.builder()
                .telephone("+22239950002")
                .nom("Sy")
                .prenom("Mamadou")
                .email("mamadou.agent@mail.mr")
                .role(RoleUtilisateur.agent_terrain)
                .roles(new String[]{"agent_terrain"})
                .plan("premium")
                .build();
        fieldAgent = utilisateurRepository.saveAndFlush(fieldAgent);
        fieldAgentToken = jwtService.generateAccessToken(fieldAgent);

        outsider = Utilisateur.builder()
                .telephone("+22239950003")
                .nom("Mint Ali")
                .prenom("Khadija")
                .email("khadija@mail.mr")
                .role(RoleUtilisateur.particulier)
                .roles(new String[]{"particulier"})
                .plan("gratuit")
                .build();
        outsider = utilisateurRepository.saveAndFlush(outsider);
        outsiderToken = jwtService.generateAccessToken(outsider);

        admin = Utilisateur.builder()
                .telephone("+22239950004")
                .nom("Admin")
                .prenom("MPX")
                .role(RoleUtilisateur.admin)
                .roles(new String[]{"admin"})
                .plan("vip")
                .build();
        admin = utilisateurRepository.saveAndFlush(admin);
        adminToken = jwtService.generateAccessToken(admin);
    }

    @Test
    @DisplayName("V5 §6 : Mandats (POST /mandats, unicité exclusivité 409, GET /mandats par rôle, PATCH /mandats/:id/status)")
    void verifierCycleDeVieMandatsEtExclusivite() throws Exception {
        String bienId = creerBienSoumis(ownerToken, "Villa Mandat Exclusif TVZ", 18.095000, -15.975000);

        String mandatPayload = String.format("""
                {
                  "bien_id": "%s",
                  "agent_id": "%s",
                  "type": "exclusif",
                  "commission_pourcentage": 5,
                  "date_debut": "2026-11-01",
                  "date_fin": "2027-05-01"
                }
                """, bienId, fieldAgent.getId());

        // 1. Un tiers non propriétaire ne peut pas créer un mandat -> 403 FORBIDDEN
        mockMvc.perform(post("/mandats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mandatPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 2. Création valide par le propriétaire -> 201 Created, status = actif, référence MAN-AAAA-NNNNN
        MvcResult createRes = mockMvc.perform(post("/mandats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mandatPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bien_id").value(bienId))
                .andExpect(jsonPath("$.agent_id").value(fieldAgent.getId().toString()))
                .andExpect(jsonPath("$.type").value("exclusif"))
                .andExpect(jsonPath("$.status").value("actif"))
                .andExpect(jsonPath("$.commission_pourcentage").value(5))
                .andReturn();

        JsonNode mandatJson = objectMapper.readTree(createRes.getResponse().getContentAsString());
        String mandatId = mandatJson.get("id").asText();
        String mandatRef = mandatJson.get("reference").asText();
        assertTrue(mandatRef.matches("^MAN-\\d{4}-\\d{5,}$"), "Référence mandat invalide: " + mandatRef);

        Bien bienLinked = bienRepository.findById(UUID.fromString(bienId)).orElseThrow();
        assertEquals(UUID.fromString(mandatId), bienLinked.getMandatId());

        // 3. Tentative de créer un second mandat pendant qu'un mandat exclusif est actif -> 409 CONFLICT
        mockMvc.perform(post("/mandats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mandatPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        // 4. GET /mandats filtré selon le rôle : propriétaire voit son mandat, agent voit le sien, tiers voit 0
        mockMvc.perform(get("/mandats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].id").value(mandatId));

        mockMvc.perform(get("/mandats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fieldAgentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].id").value(mandatId));

        mockMvc.perform(get("/mandats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        // 5. Résiliation du mandat (PATCH /mandats/:id/status) -> libère bien.mandatId
        mockMvc.perform(patch("/mandats/" + mandatId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"resilie\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("resilie"));

        Bien bienUnlinked = bienRepository.findById(UUID.fromString(bienId)).orElseThrow();
        assertNull(bienUnlinked.getMandatId(), "La résiliation du mandat doit libérer bien.mandatId");
    }

    @Test
    @DisplayName("V5 §7 : Missions terrain (POST assignation, GET /missions, GET détail GPS exact, POST photos et PATCH rapport PostGIS)")
    void verifierOperationsTerrainEtPhotosVerification() throws Exception {
        String bienId = creerBienSoumis(ownerToken, "Terrain Vérification Terrain Sukuk", 18.121000, -15.986500);

        String assignPayload = String.format("""
                {
                  "bien_id": "%s",
                  "agent_id": "%s",
                  "type": "verification_physique"
                }
                """, bienId, fieldAgent.getId());

        // 1. Seul le back-office (admin / verificateur) peut assigner une mission -> 403 pour un particulier
        mockMvc.perform(post("/missions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 2. Assignation par l'admin -> 201 Created, MIS-AAAA-NNNNN, le bien passe automatiquement en 'en_verification'
        MvcResult createMissionRes = mockMvc.perform(post("/missions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("assignee"))
                .andExpect(jsonPath("$.bien_id").value(bienId))
                .andExpect(jsonPath("$.agent_id").value(fieldAgent.getId().toString()))
                .andReturn();

        JsonNode missionJson = objectMapper.readTree(createMissionRes.getResponse().getContentAsString());
        String missionId = missionJson.get("id").asText();
        String missionRef = missionJson.get("reference").asText();
        assertTrue(missionRef.matches("^MIS-\\d{4}-\\d{5,}$"), "Référence mission invalide: " + missionRef);

        Bien bienEnVerif = bienRepository.findById(UUID.fromString(bienId)).orElseThrow();
        assertEquals(StatutBien.en_verification, bienEnVerif.getStatut());

        // 3. L'agent connecté retrouve sa mission dans GET /missions?status=assignee
        mockMvc.perform(get("/missions")
                        .param("status", "assignee")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fieldAgentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].id").value(missionId));

        // 4. GET /missions/:id interdit à un tiers (403), mais expose le GPS exact du bien à l'agent assigné
        mockMvc.perform(get("/missions/" + missionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/missions/" + missionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fieldAgentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bien.localisation.latitude").value(18.121))
                .andExpect(jsonPath("$.bien.localisation.longitude").value(-15.9865))
                .andExpect(jsonPath("$.bien.adresse").value("Lot Exact 402, Zone Sukuk"));

        // 5. Upload photos de vérification (POST /missions/:id/photos) + rejet > 5 Mo (413 FILE_TOO_LARGE)
        MockMultipartFile vPhoto = new MockMultipartFile(
                "photos",
                "bornage_terrain.jpg",
                "image/jpeg",
                "jpeg-verification-bytes".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/missions/" + missionId + "/photos")
                        .file(vPhoto)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fieldAgentToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photos.length()").value(1));

        MockMultipartFile oversizedPhoto = new MockMultipartFile(
                "photos",
                "oversized.jpg",
                "image/jpeg",
                new byte[6 * 1024 * 1024]
        );
        mockMvc.perform(multipart("/missions/" + missionId + "/photos")
                        .file(oversizedPhoto)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fieldAgentToken))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

        // 6. Clôture de la mission avec rapport et relevé GPS PostGIS (PATCH /missions/:id)
        String completePayload = """
                {
                  "status": "terminee",
                  "rapport": "Bien conforme, bornes vérifiées sur place",
                  "localisation_visite": { "latitude": 18.12105, "longitude": -15.98648 },
                  "precision_m": 4.5,
                  "checklist": { "bornes_presentes": true, "acces_voirie": true }
                }
                """;

        MvcResult patchRes = mockMvc.perform(patch("/missions/" + missionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + fieldAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("terminee"))
                .andExpect(jsonPath("$.rapport").value("Bien conforme, bornes vérifiées sur place"))
                .andExpect(jsonPath("$.localisation_visite.latitude").value(18.12105))
                .andExpect(jsonPath("$.localisation_visite.longitude").value(-15.98648))
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andReturn();

        assertNotNull(objectMapper.readTree(patchRes.getResponse().getContentAsString()).get("date_realisee"));
    }

    @Test
    @DisplayName("V5 §13 Checklist #14 : Documentation OpenAPI 3 (/v3/api-docs) et Swagger UI (/api/docs) accessibles publiquement")
    void verifierOpenApiEtSwaggerUiPublics() throws Exception {
        MvcResult apiDocsRes = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").isNotEmpty())
                .andExpect(jsonPath("$.info.title").value("Foncier Mauritanie (MPX) — API Backend"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andReturn();

        JsonNode pathsNode = objectMapper.readTree(apiDocsRes.getResponse().getContentAsString()).get("paths");
        assertNotNull(pathsNode);
        assertTrue(pathsNode.has("/mandats"), "Le schéma OpenAPI doit documenter /mandats");
        assertTrue(pathsNode.has("/missions"), "Le schéma OpenAPI doit documenter /missions");
        assertTrue(pathsNode.has("/properties"), "Le schéma OpenAPI doit documenter /properties");

        // /api/docs redirige vers l'UI Swagger (/api/swagger-ui/index.html) : toute la chaîne doit répondre 200 sans JWT
        MvcResult swaggerUiRes = mockMvc.perform(get("/api/docs")).andReturn();
        int httpStatus = swaggerUiRes.getResponse().getStatus();
        assertTrue(httpStatus == 200 || (httpStatus >= 300 && httpStatus < 400),
                "/api/docs doit répondre 200 ou 3xx sans JWT, reçu: " + httpStatus);

        if (httpStatus >= 300 && httpStatus < 400) {
            String redirectedUrl = swaggerUiRes.getResponse().getRedirectedUrl();
            assertNotNull(redirectedUrl, "L'URL de redirection Swagger UI ne doit pas être nulle");
            mockMvc.perform(get(redirectedUrl))
                    .andExpect(status().isOk());
        }

        // Vérifier également swagger-config sans JWT
        MvcResult swaggerConfigRes = mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode swaggerConfigNode = objectMapper.readTree(swaggerConfigRes.getResponse().getContentAsString());
        assertNotNull(swaggerConfigNode.get("url"));
    }

    private String creerBienSoumis(String tokenProprio, String titre, double lat, double lng) throws Exception {
        String body = String.format(java.util.Locale.US, """
                {
                  "titre": "%s",
                  "description": "Description détaillée de %s",
                  "type": "terrain",
                  "transaction": "vente",
                  "prix": 16000000,
                  "surface_m2": 500,
                  "adresse": "Lot Exact 402, Zone Sukuk",
                  "ville": "Nouakchott",
                  "quartier": "Sukuk",
                  "localisation": { "latitude": %.6f, "longitude": %.6f },
                  "status": "soumis"
                }
                """, titre, titre, lat, lng);

        MvcResult res = mockMvc.perform(post("/properties")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProprio)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }
}
