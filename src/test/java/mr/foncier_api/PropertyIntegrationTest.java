package mr.foncier_api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mr.foncier_api.entity.Bien;
import mr.foncier_api.entity.Utilisateur;
import mr.foncier_api.enums.RoleUtilisateur;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class PropertyIntegrationTest {

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    private String ownerToken;
    private String otherUserToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        otpAndTokenStore.clearAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        Utilisateur owner = Utilisateur.builder()
                .telephone("+22239880001")
                .nom("Sidi")
                .prenom("Ahmed")
                .email("ahmed.owner@mail.mr")
                .role(RoleUtilisateur.proprietaire)
                .roles(new String[]{"proprietaire"})
                .build();
        owner = utilisateurRepository.saveAndFlush(owner);
        ownerToken = jwtService.generateAccessToken(owner);

        Utilisateur otherUser = Utilisateur.builder()
                .telephone("+22239880002")
                .nom("Kane")
                .prenom("Fatimata")
                .role(RoleUtilisateur.particulier)
                .roles(new String[]{"particulier"})
                .build();
        otherUser = utilisateurRepository.saveAndFlush(otherUser);
        otherUserToken = jwtService.generateAccessToken(otherUser);

        Utilisateur admin = Utilisateur.builder()
                .telephone("+22239880003")
                .nom("Admin")
                .prenom("MPX")
                .role(RoleUtilisateur.admin)
                .roles(new String[]{"admin"})
                .build();
        admin = utilisateurRepository.saveAndFlush(admin);
        adminToken = jwtService.generateAccessToken(admin);
    }

    @Test
    @DisplayName("Checklist #7 : Vérification de la présence des index spatiaux PostGIS GIST sur public.bien")
    void verifierIndexGistPostgis() {
        Integer gistCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'bien'
                  AND indexname IN ('idx_bien_position_floue_gist', 'idx_bien_position_exacte_gist')
                """,
                Integer.class
        );
        assertEquals(2, gistCount, "Les 2 index GIST PostGIS doivent exister sur public.bien");
    }

    @Test
    @DisplayName("V3 : Création Bien (FM-AAAA-NNNNN), sécurité propriétaire (401/403), publication & floutage GPS (EX-030/031)")
    void verifierCreationSecuritePublicationEtFloutageGps() throws Exception {
        String createPayload = """
                {
                  "titre": "Villa Test Ilot K",
                  "description": "Superbe villa moderne",
                  "type": "maison",
                  "transaction": "vente",
                  "prix": 28000000,
                  "surface_m2": 400,
                  "nombre_chambres": 4,
                  "nombre_salles_bain": 3,
                  "adresse": "Rue 42-108 Porte Exacte 12, Ilot K",
                  "ville": "Nouakchott",
                  "quartier": "Tevragh Zeina",
                  "localisation": { "latitude": 18.095000, "longitude": -15.975000 },
                  "status": "soumis"
                }
                """;

        // 1. Sans token -> 401 UNAUTHORIZED
        mockMvc.perform(post("/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        // 2. Création par le propriétaire -> 201 Created + référence FM-AAAA-NNNNN
        MvcResult createRes = mockMvc.perform(post("/properties")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.reference").isNotEmpty())
                .andExpect(jsonPath("$.status").value("soumis"))
                .andExpect(jsonPath("$.localisation.latitude").value(18.095))
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createRes.getResponse().getContentAsString());
        String propertyId = createdJson.get("id").asText();
        String ref = createdJson.get("reference").asText();
        assertTrue(ref.matches("^FM-\\d{4}-\\d{5,}$"), "La référence doit respecter le format FM-AAAA-NNNNN: " + ref);

        // 3. Un bien 'soumis' (non publié) est invisible pour un tiers (404), mais visible pour son propriétaire (200)
        mockMvc.perform(get("/properties/" + propertyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUserToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/properties/" + propertyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk());

        // 4. Modification par un autre utilisateur -> 403 FORBIDDEN (EX-002)
        mockMvc.perform(put("/properties/" + propertyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prix\": 1000}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 5. Modification par le propriétaire -> 200 OK
        mockMvc.perform(put("/properties/" + propertyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prix\": 27500000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prix").value(27500000));

        // 6. Publication par l'administrateur -> 200 OK
        mockMvc.perform(patch("/properties/" + propertyId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"publie\", \"est_verifie\": true, \"est_vip\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("publie"))
                .andExpect(jsonPath("$.est_verifie").value(true));

        // 7. Consultation publique : compteur de vues incrémenté + floutage GPS anti-contournement (EX-030)
        MvcResult publicDetailRes = mockMvc.perform(get("/properties/" + propertyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proprietaire.nom").value("Sidi"))
                .andExpect(jsonPath("$.proprietaire.telephone").doesNotExist())
                .andReturn();

        JsonNode publicJson = objectMapper.readTree(publicDetailRes.getResponse().getContentAsString());
        double fuzzyLat = publicJson.get("localisation").get("latitude").asDouble();
        double fuzzyLng = publicJson.get("localisation").get("longitude").asDouble();
        int views1 = publicJson.get("nombre_vues").asInt();

        assertNotEquals(18.095000, fuzzyLat, 0.00001, "EX-030 : La latitude publique doit être floutée");
        assertNotEquals(-15.975000, fuzzyLng, 0.00001, "EX-030 : La longitude publique doit être floutée");
        assertEquals("Tevragh Zeina", publicJson.get("adresse").asText(), "L'adresse exacte doit être masquée au niveau public");

        // Deuxième consultation -> nombre_vues incrémenté de +1
        mockMvc.perform(get("/properties/" + propertyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre_vues").value(views1 + 1));
    }

    @Test
    @DisplayName("V3 : Recherche géospatiale PostGIS (/nearby et filtres /properties) : inclusion proche vs exclusion distante")
    void verifierRechercheGeospatialePostgisEtFiltres() throws Exception {
        // Bien 1 : Proche du point de recherche (Tevragh Zeina : 18.0910, -15.9710)
        String idProche = creerEtPublierBien(
                "Appartement Proche TVZ",
                "appartement",
                "location",
                55000,
                "Nouakchott",
                18.0910,
                -15.9710
        );

        // Bien 2 : Très éloigné à Nouadhibou (~340 km : 20.9000, -17.0500)
        String idLoin = creerEtPublierBien(
                "Appartement Distant Nouadhibou",
                "appartement",
                "location",
                40000,
                "Nouadhibou",
                20.9000,
                -17.0500
        );

        // Recherche PostGIS /properties/nearby dans un rayon de 3 km autour de (18.0900, -15.9700)
        MvcResult nearbyRes = mockMvc.perform(get("/properties/nearby")
                        .param("lat", "18.0900")
                        .param("lng", "-15.9700")
                        .param("rayon_km", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        String nearbyBody = nearbyRes.getResponse().getContentAsString();
        assertTrue(nearbyBody.contains(idProche), "Le bien situé à ~200m à Tevragh Zeina doit remonter dans /nearby");
        assertTrue(!nearbyBody.contains(idLoin), "Le bien situé à Nouadhibou (~340km) ne doit pas remonter dans un rayon de 3km");

        // Recherche plein texte ?q=Nouadhibou + filtre ville
        MvcResult searchRes = mockMvc.perform(get("/properties")
                        .param("ville", "Nouadhibou")
                        .param("q", "Distant"))
                .andExpect(status().isOk())
                .andReturn();

        String searchBody = searchRes.getResponse().getContentAsString();
        assertTrue(searchBody.contains(idLoin));
        assertTrue(!searchBody.contains(idProche));
    }

    @Test
    @DisplayName("V3 : Upload de photos (POST /properties/:id/photos) : 201 Created et rejet > 5 Mo (413 FILE_TOO_LARGE)")
    void verifierUploadPhotosBien() throws Exception {
        String propertyId = creerEtPublierBien(
                "Terrain Photo Test",
                "terrain",
                "vente",
                12000000,
                "Nouakchott",
                18.0850,
                -15.9700
        );

        MockMultipartFile p1 = new MockMultipartFile(
                "photos",
                "facade.jpg",
                "image/jpeg",
                "img-bytes-1".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile p2 = new MockMultipartFile(
                "photos",
                "jardin.png",
                "image/png",
                "img-bytes-2".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/properties/" + propertyId + "/photos")
                        .file(p1)
                        .file(p2)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photos.length()").value(2));

        // Fichier de 6 Mo (> limite de 5 Mo par photo de bien) -> 413 FILE_TOO_LARGE
        byte[] sixMb = new byte[6 * 1024 * 1024];
        MockMultipartFile oversized = new MockMultipartFile(
                "photos",
                "huge.jpg",
                "image/jpeg",
                sixMb
        );

        mockMvc.perform(multipart("/properties/" + propertyId + "/photos")
                        .file(oversized)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    @DisplayName("V3 : Mes biens (/users/me/properties) et Favoris (POST / GET / DELETE /users/me/favorites)")
    void verifierMesBiensEtFavoris() throws Exception {
        String propId = creerEtPublierBien(
                "Bureau Favori Test",
                "bureau",
                "location",
                80000,
                "Nouakchott",
                18.0880,
                -15.9650
        );

        // 1. Le propriétaire retrouve son bien dans GET /users/me/properties
        mockMvc.perform(get("/users/me/properties")
                        .param("status", "publie")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].id").value(propId));

        // 2. L'autre utilisateur ajoute ce bien à ses favoris (idempotent)
        mockMvc.perform(post("/users/me/favorites/" + propId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.est_favori").value(true));

        mockMvc.perform(post("/users/me/favorites/" + propId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUserToken))
                .andExpect(status().isOk());

        Bien bienAfterAdd = bienRepository.findById(UUID.fromString(propId)).orElseThrow();
        assertEquals(1, bienAfterAdd.getFavoris(), "Le compteur favoris du bien doit valoir 1 malgré le double appel");

        // 3. Liste des favoris -> contient le bien avec est_favori = true
        mockMvc.perform(get("/users/me/favorites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].id").value(propId))
                .andExpect(jsonPath("$.data[0].est_favori").value(true));

        // 4. Le détail du bien affiche également est_favori = true pour cet utilisateur
        mockMvc.perform(get("/properties/" + propId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.est_favori").value(true));

        // 5. Suppression du favori -> compteur repasse à 0 et liste vide
        mockMvc.perform(delete("/users/me/favorites/" + propId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.est_favori").value(false));

        Bien bienAfterRemove = bienRepository.findById(UUID.fromString(propId)).orElseThrow();
        assertEquals(0, bienAfterRemove.getFavoris());

        mockMvc.perform(get("/users/me/favorites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    private String creerEtPublierBien(
            String titre,
            String type,
            String transaction,
            long prix,
            String ville,
            double lat,
            double lng
    ) throws Exception {
        String body = String.format(java.util.Locale.US, """
                {
                  "titre": "%s",
                  "description": "Description détaillée de %s",
                  "type": "%s",
                  "transaction": "%s",
                  "prix": %d,
                  "surface_m2": 180,
                  "ville": "%s",
                  "quartier": "Centre",
                  "localisation": { "latitude": %.6f, "longitude": %.6f },
                  "status": "soumis"
                }
                """, titre, titre, type, transaction, prix, ville, lat, lng);

        MvcResult res = mockMvc.perform(post("/properties")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/properties/" + id + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"publie\", \"est_verifie\": true}"))
                .andExpect(status().isOk());

        return id;
    }
}
