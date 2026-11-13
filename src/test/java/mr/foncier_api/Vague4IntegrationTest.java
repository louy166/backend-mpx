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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
class Vague4IntegrationTest {

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

    private String ownerToken;
    private String freeBuyerToken;
    private String subscriberToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        otpAndTokenStore.clearAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        Utilisateur owner = Utilisateur.builder()
                .telephone("+22239940001")
                .nom("Ould Ahmed")
                .prenom("Brahim")
                .email("brahim.proprio@mail.mr")
                .role(RoleUtilisateur.proprietaire)
                .roles(new String[]{"proprietaire"})
                .plan("gratuit")
                .build();
        owner = utilisateurRepository.saveAndFlush(owner);
        ownerToken = jwtService.generateAccessToken(owner);

        Utilisateur freeBuyer = Utilisateur.builder()
                .telephone("+22239940002")
                .nom("Mint Sidi")
                .prenom("Aichetou")
                .email("aichetou@mail.mr")
                .role(RoleUtilisateur.particulier)
                .roles(new String[]{"particulier"})
                .plan("gratuit")
                .build();
        freeBuyer = utilisateurRepository.saveAndFlush(freeBuyer);
        freeBuyerToken = jwtService.generateAccessToken(freeBuyer);

        Utilisateur subscriber = Utilisateur.builder()
                .telephone("+22239940003")
                .nom("Diallo")
                .prenom("Moussa")
                .email("moussa@mail.mr")
                .role(RoleUtilisateur.proprietaire)
                .roles(new String[]{"proprietaire"})
                .plan("gratuit")
                .build();
        subscriber = utilisateurRepository.saveAndFlush(subscriber);
        subscriberToken = jwtService.generateAccessToken(subscriber);

        Utilisateur admin = Utilisateur.builder()
                .telephone("+22239940004")
                .nom("Admin")
                .prenom("System")
                .role(RoleUtilisateur.admin)
                .roles(new String[]{"admin"})
                .plan("vip")
                .build();
        admin = utilisateurRepository.saveAndFlush(admin);
        adminToken = jwtService.generateAccessToken(admin);
    }

    @Test
    @DisplayName("V4 §5 : Upload de documents fonciers (POST /properties/:id/documents), validation types, 10 Mo max (413) et confidentialité URL")
    void verifierUploadDocumentsBienEtConfidentialite() throws Exception {
        String propertyId = creerEtPublierBien(ownerToken, "Villa Titrée TVZ", 18.095000, -15.975000);

        MockMultipartFile pdfDoc = new MockMultipartFile(
                "file",
                "titre_foncier_102.pdf",
                "application/pdf",
                "%PDF-1.4 contenu titre foncier".getBytes(StandardCharsets.UTF_8)
        );

        // 1. Upload par un tiers non propriétaire -> 403 FORBIDDEN
        mockMvc.perform(multipart("/properties/" + propertyId + "/documents")
                        .file(pdfDoc)
                        .param("type", "titre_foncier")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + freeBuyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 2. Type de document non prévu par le contrat -> 400 VALIDATION_ERROR
        mockMvc.perform(multipart("/properties/" + propertyId + "/documents")
                        .file(pdfDoc)
                        .param("type", "type_invalide")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 3. Upload valide par le propriétaire -> 201 Created avec url, taille_octets et verifie=false
        MvcResult uploadRes = mockMvc.perform(multipart("/properties/" + propertyId + "/documents")
                        .file(pdfDoc)
                        .param("type", "titre_foncier")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.bien_id").value(propertyId))
                .andExpect(jsonPath("$.type").value("titre_foncier"))
                .andExpect(jsonPath("$.nom_fichier").value("titre_foncier_102.pdf"))
                .andExpect(jsonPath("$.url").isNotEmpty())
                .andExpect(jsonPath("$.taille_octets").value(pdfDoc.getSize()))
                .andExpect(jsonPath("$.verifie").value(false))
                .andReturn();

        String docUrl = objectMapper.readTree(uploadRes.getResponse().getContentAsString()).get("url").asText();
        assertTrue(docUrl.contains("/uploads/properties/" + propertyId + "/documents/"));

        // 4. Fichier > 10 Mo (11 Mo) -> 413 FILE_TOO_LARGE
        byte[] elevenMb = new byte[11 * 1024 * 1024];
        MockMultipartFile oversizedDoc = new MockMultipartFile(
                "file",
                "scan_lourd.pdf",
                "application/pdf",
                elevenMb
        );
        mockMvc.perform(multipart("/properties/" + propertyId + "/documents")
                        .file(oversizedDoc)
                        .param("type", "acte_vente")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

        // 5. Visiteur non déverrouillé : métadonnées du titre visibles mais URL confidentielle masquée (null)
        MvcResult publicGet = mockMvc.perform(get("/properties/" + propertyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + freeBuyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents.length()").value(1))
                .andExpect(jsonPath("$.documents[0].nom_fichier").value("titre_foncier_102.pdf"))
                .andReturn();

        JsonNode pubDocNode = objectMapper.readTree(publicGet.getResponse().getContentAsString())
                .get("documents").get(0).get("url");
        assertTrue(pubDocNode == null || pubDocNode.isNull(), "L'URL d'un titre foncier doit être masquée avant déverrouillage N3");

        // 6. Propriétaire lui-même : URL bien présente
        mockMvc.perform(get("/properties/" + propertyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].url").value(docUrl));
    }

    @Test
    @DisplayName("V4 §10 : Plans publics (/plans), plafond d'annonces gratuit (2 max), souscription (/subscriptions) et factures (/me/invoices)")
    void verifierPlansSouscriptionPlafondAnnoncesEtFactures() throws Exception {
        // 1. GET /plans est public (sans JWT) et retourne les 4 plans exacts du Contrat API §10
        mockMvc.perform(get("/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").value("gratuit"))
                .andExpect(jsonPath("$[0].prix").value(0))
                .andExpect(jsonPath("$[0].limite_annonces").value(2))
                .andExpect(jsonPath("$[1].id").value("basic"))
                .andExpect(jsonPath("$[1].nom").value("Starter"))
                .andExpect(jsonPath("$[1].prix").value(5000))
                .andExpect(jsonPath("$[2].id").value("premium"))
                .andExpect(jsonPath("$[2].nom").value("Pro"))
                .andExpect(jsonPath("$[2].prix").value(15000))
                .andExpect(jsonPath("$[3].id").value("vip"))
                .andExpect(jsonPath("$[3].prix").value(50000))
                .andExpect(jsonPath("$[3].limite_annonces").value(-1));

        // 2. Utilisateur en plan gratuit : peut créer 2 annonces, mais la 3e est bloquée (403 FORBIDDEN)
        creerEtPublierBien(subscriberToken, "Annonce 1 Plan Gratuit", 18.0810, -15.9710);
        creerEtPublierBien(subscriberToken, "Annonce 2 Plan Gratuit", 18.0820, -15.9720);

        String thirdPropertyPayload = """
                {
                  "titre": "Annonce 3 Au-Delà Du Quota",
                  "description": "Devrait être bloquée par la limite de 2 annonces",
                  "type": "terrain",
                  "transaction": "vente",
                  "prix": 5000000,
                  "ville": "Nouakchott",
                  "quartier": "Riyad",
                  "localisation": { "latitude": 18.0300, "longitude": -15.9600 }
                }
                """;

        mockMvc.perform(post("/properties")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + subscriberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(thirdPropertyPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 3. Souscription au plan 'basic' (Starter : 10 annonces, 5 déverrouillages, 5000 MRU)
        String subPayload = """
                {
                  "plan_id": "basic",
                  "methode_paiement": "mobile_money",
                  "numero_paiement": "+22237123456"
                }
                """;

        mockMvc.perform(post("/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + subscriberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plan_id").value("basic"))
                .andExpect(jsonPath("$.plan_nom").value("Starter"))
                .andExpect(jsonPath("$.statut").value("actif"))
                .andExpect(jsonPath("$.prix").value(5000))
                .andExpect(jsonPath("$.limite_annonces").value(10))
                .andExpect(jsonPath("$.deverrouillages_inclus").value(5))
                .andExpect(jsonPath("$.deverrouillages_restants").value(5));

        // 4. La 3e annonce passe désormais (201 Created) !
        mockMvc.perform(post("/properties")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + subscriberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(thirdPropertyPayload))
                .andExpect(status().isCreated());

        // 5. Vérification de GET /users/me/subscription et GET /users/me/invoices
        mockMvc.perform(get("/users/me/subscription")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + subscriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan_id").value("basic"));

        MvcResult invoicesRes = mockMvc.perform(get("/users/me/invoices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + subscriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].type").value("abonnement"))
                .andExpect(jsonPath("$.data[0].montant").value(5000))
                .andExpect(jsonPath("$.data[0].devise").value("MRU"))
                .andExpect(jsonPath("$.data[0].statut").value("paye"))
                .andReturn();

        String invoiceRef = objectMapper.readTree(invoicesRes.getResponse().getContentAsString())
                .get("data").get(0).get("reference").asText();
        assertTrue(invoiceRef.matches("^FAC-\\d{4}-\\d{5,}$"), "Référence facture invalide: " + invoiceRef);
    }

    @Test
    @DisplayName("V4 §8 & §9 : Déverrouillage à l'acte (500 MRU) vs sur quota (0 MRU), levée du floutage GPS et Notifications in-app")
    void verifierDeverrouillagesQuotaEtNotifications() throws Exception {
        String propertyId = creerEtPublierBien(ownerToken, "Maison Déverrouillage Ilot C", 18.095000, -15.975000);

        MockMultipartFile pdfDoc = new MockMultipartFile(
                "file",
                "acte_notarie.pdf",
                "application/pdf",
                "%PDF-1.4 acte".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/properties/" + propertyId + "/documents")
                        .file(pdfDoc)
                        .param("type", "acte_vente")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isCreated());

        // 1. Acheteur sans abonnement (plan gratuit) déverrouille le bien -> 500 MRU facturés
        mockMvc.perform(post("/properties/" + propertyId + "/unlock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + freeBuyerToken)
                        .header("User-Agent", "MPX-Mobile/1.0")
                        .header("X-Device-Id", "ios-device-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telephone_proprietaire").value("+22239940001"))
                .andExpect(jsonPath("$.email_proprietaire").value("brahim.proprio@mail.mr"))
                .andExpect(jsonPath("$.prix_paye").value(500));

        // 2. Idempotence : un second appel pour le même bien renvoie prix_paye = 0 sans incrémenter le compteur
        mockMvc.perform(post("/properties/" + propertyId + "/unlock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + freeBuyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prix_paye").value(0));

        Bien bienAfterFirstUnlock = bienRepository.findById(UUID.fromString(propertyId)).orElseThrow();
        assertEquals(1, bienAfterFirstUnlock.getDeverrouillages());

        // 3. GET /properties/:id expose maintenant la position GPS exacte, l'adresse complète et l'URL du document !
        mockMvc.perform(get("/properties/" + propertyId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + freeBuyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localisation.latitude").value(18.095))
                .andExpect(jsonPath("$.localisation.longitude").value(-15.975))
                .andExpect(jsonPath("$.adresse").value("Adresse Exacte Lot 77, Ilot C"))
                .andExpect(jsonPath("$.documents[0].url").isNotEmpty());

        // 4. Historique des déverrouillages de l'acheteur (GET /users/me/unlocks) + facture générée
        mockMvc.perform(get("/users/me/unlocks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + freeBuyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].bien_id").value(propertyId))
                .andExpect(jsonPath("$.data[0].prix_paye").value(500));

        mockMvc.perform(get("/users/me/invoices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + freeBuyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].type").value("deverrouillage"))
                .andExpect(jsonPath("$.data[0].montant").value(500));

        // 5. Abonné 'basic' (5 déverrouillages inclus) déverrouille le même bien -> prix_paye = 0 (pris sur quota)
        mockMvc.perform(post("/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + subscriberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "plan_id": "basic",
                                  "methode_paiement": "carte_bancaire"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/properties/" + propertyId + "/unlock")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + subscriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telephone_proprietaire").value("+22239940001"))
                .andExpect(jsonPath("$.prix_paye").value(0));

        mockMvc.perform(get("/users/me/subscription")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + subscriberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deverrouillages_utilises").value(1))
                .andExpect(jsonPath("$.deverrouillages_restants").value(4));

        // 6. Notifications in-app du propriétaire : 2 notifications non lues reçues suite aux 2 déverrouillages !
        MvcResult notifRes = mockMvc.perform(get("/notifications")
                        .param("lu", "false")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andReturn();

        JsonNode notifData = objectMapper.readTree(notifRes.getResponse().getContentAsString()).get("data");
        String firstNotifId = notifData.get(0).get("id").asText();
        assertFalse(notifData.get(0).get("lu").asBoolean());

        // Un autre utilisateur ne peut pas marquer la notification du propriétaire (404 NOT_FOUND)
        mockMvc.perform(patch("/notifications/" + firstNotifId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + freeBuyerToken))
                .andExpect(status().isNotFound());

        // PATCH /notifications/:id/read par le propriétaire -> lu = true, lu_le renseigné
        MvcResult readOneRes = mockMvc.perform(patch("/notifications/" + firstNotifId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lu").value(true))
                .andReturn();
        assertNotNull(objectMapper.readTree(readOneRes.getResponse().getContentAsString()).get("lu_le"));

        // Il reste 1 notification non lue -> POST /notifications/read-all marque tout comme lu
        mockMvc.perform(post("/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated_count").value(1));

        mockMvc.perform(get("/notifications")
                        .param("lu", "false")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    private String creerEtPublierBien(String tokenProprio, String titre, double lat, double lng) throws Exception {
        String body = String.format(java.util.Locale.US, """
                {
                  "titre": "%s",
                  "description": "Description détaillée de %s",
                  "type": "maison",
                  "transaction": "vente",
                  "prix": 25000000,
                  "surface_m2": 320,
                  "adresse": "Adresse Exacte Lot 77, Ilot C",
                  "ville": "Nouakchott",
                  "quartier": "Tevragh Zeina",
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

        String id = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/properties/" + id + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"publie\", \"est_verifie\": true}"))
                .andExpect(status().isOk());

        return id;
    }
}
