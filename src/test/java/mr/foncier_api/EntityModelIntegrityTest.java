package mr.foncier_api;

import jakarta.persistence.EntityManager;
import mr.foncier_api.entity.Abonnement;
import mr.foncier_api.entity.Bien;
import mr.foncier_api.entity.Favori;
import mr.foncier_api.entity.FormuleAbonnement;
import mr.foncier_api.entity.Notification;
import mr.foncier_api.entity.Utilisateur;
import mr.foncier_api.enums.RoleUtilisateur;
import mr.foncier_api.enums.StatutBien;
import mr.foncier_api.enums.TypeBien;
import mr.foncier_api.enums.TypeTransactionBien;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class EntityModelIntegrityTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Schéma unique public : présence exacte des 11 tables essentielles et absence des schémas vip/capital")
    void verifierSchemaPublicUniqueEtOnzeTablesEssentielles() {
        assertNotNull(entityManager);

        Integer essentialTables = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                      'utilisateur', 'bien', 'favori', 'document', 'mandat',
                      'mission_terrain', 'deverrouillage', 'notification',
                      'formule_abonnement', 'abonnement', 'transaction_financiere'
                  )
                """,
                Integer.class
        );
        assertEquals(11, essentialTables, "Les 11 tables essentielles du Contrat API Frontend doivent exister dans public.");

        Integer removedSchemas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name IN ('vip', 'capital')",
                Integer.class
        );
        assertEquals(0, removedSchemas, "Les anciens schémas vip et capital ne doivent plus exister.");

        Integer obsoletePublicTables = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                      'bail', 'echeance', 'lead', 'lead_interaction',
                      'partenaire_pro', 'mission_partenaire', 'achat_acte',
                      'referentiel_prix', 'referentiel_wilaya', 'referentiel_moughataa',
                      'referentiel_commune', 'referentiel_quartier',
                      'historique_etat', 'journal_audit'
                  )
                """,
                Integer.class
        );
        assertEquals(0, obsoletePublicTables, "Aucune des 14 tables secondaires retirées ne doit subsister dans public.");
    }

    @Test
    @DisplayName("Contrat API §2, §4, §9, §10 : Auto-création OTP, Brouillon Bien Frontend, Favori, Notification & Abonnement")
    void verifierAlignementContratApiFrontend() {
        Utilisateur otpUser = Utilisateur.builder()
                .telephone("+22239990001")
                .deviceTokens(new String[]{"fcm-token-123"})
                .build();
        entityManager.persist(otpUser);
        entityManager.flush();

        assertNotNull(otpUser.getId());
        assertEquals(RoleUtilisateur.particulier, otpUser.getRole());
        assertEquals("gratuit", otpUser.getPlan());
        assertNull(otpUser.getNom());
        assertFalse(otpUser.isKycVerifie());

        GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
        Point loc = gf.createPoint(new Coordinate(-15.97, 18.09));

        Bien brouillonFrontend = Bien.builder()
                .reference("FM-2099-99998")
                .titre("Villa 4 chambres Tevragh Zeina")
                .descriptionFr("Belle villa spacieuse")
                .type(TypeBien.maison)
                .transaction(TypeTransactionBien.colocation)
                .prix(new BigDecimal("25000000.00"))
                .superficieM2(new BigDecimal("350.00"))
                .nbChambres(4)
                .nbSdb(3)
                .adresse("Ilot K, Tevragh Zeina")
                .ville("Nouakchott")
                .quartierNom("Tevragh Zeina")
                .positionExacte(loc)
                .photos(new String[]{"https://cdn.fonciermauritanie.mr/p1.jpg"})
                .statut(StatutBien.brouillon)
                .proprietaire(otpUser)
                .build();
        entityManager.persist(brouillonFrontend);
        entityManager.flush();
        assertNotNull(brouillonFrontend.getId());

        Favori favori = Favori.builder()
                .utilisateur(otpUser)
                .bien(brouillonFrontend)
                .build();
        entityManager.persist(favori);

        Notification notif = Notification.builder()
                .utilisateur(otpUser)
                .titre("Bienvenue sur MPX")
                .message("Votre compte a été créé avec succès.")
                .build();
        entityManager.persist(notif);

        FormuleAbonnement planPro = FormuleAbonnement.builder()
                .code("premium_test")
                .nomFr("Pro")
                .nomAr("برو")
                .tarifAnnuel(new BigDecimal("15000.00"))
                .limiteAnnonces(50)
                .deverrouillagesInclus(30)
                .build();
        entityManager.persist(planPro);

        Abonnement sub = Abonnement.builder()
                .utilisateur(otpUser)
                .formule(planPro)
                .dateDebut(LocalDate.now())
                .dateFin(LocalDate.now().plusYears(1))
                .methodePaiement("mobile_money")
                .numeroPaiement("+22239990001")
                .build();
        entityManager.persist(sub);
        entityManager.flush();

        assertNotNull(favori.getId());
        assertNotNull(notif.getId());
        assertNotNull(sub.getId());
    }

    @Test
    @DisplayName("PostGIS & EX-033 : Persistance spatiale d'un Bien (Point SRID 4326) et contrôle du prix conclu")
    void verifierPersistanceSpatialeEtPrixConclu() {
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        Point positionFloue = geometryFactory.createPoint(new Coordinate(-15.9785, 18.0858));
        Point positionExacte = geometryFactory.createPoint(new Coordinate(-15.9750, 18.0865));

        Utilisateur proprietaire = Utilisateur.builder()
                .telephone("+22246990002")
                .motDePasseHash("hashed_pwd")
                .nom("Ba")
                .prenom("Amadou")
                .roles(new String[]{"proprietaire"})
                .build();
        entityManager.persist(proprietaire);

        Bien bien = Bien.builder()
                .reference("FM-2099-99999")
                .type(TypeBien.villa)
                .transaction(TypeTransactionBien.vente)
                .titre("Villa moderne Ilot K")
                .descriptionFr("Grande villa titrée à Tevragh Zeina")
                .descriptionAr("فيلا حديثة في تفرغ زينة")
                .superficieM2(new BigDecimal("600.00"))
                .prix(new BigDecimal("25000000.00"))
                .ville("Nouakchott")
                .quartierNom("Tevragh Zeina")
                .positionFloue(positionFloue)
                .positionExacte(positionExacte)
                .proprietaire(proprietaire)
                .build();

        entityManager.persist(bien);
        entityManager.flush();
        assertNotNull(bien.getId());

        bien.setStatut(StatutBien.vendu);
        assertThrows(Exception.class, () -> entityManager.flush(),
                "EX-033 : Le passage à l'état vendu sans prixConclu doit être bloqué.");
    }
}
