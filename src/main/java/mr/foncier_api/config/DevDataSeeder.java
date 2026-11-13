package mr.foncier_api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mr.foncier_api.entity.Bien;
import mr.foncier_api.entity.Mandat;
import mr.foncier_api.entity.MissionTerrain;
import mr.foncier_api.entity.Utilisateur;
import mr.foncier_api.enums.Langue;
import mr.foncier_api.enums.RoleUtilisateur;
import mr.foncier_api.enums.StatutBien;
import mr.foncier_api.enums.StatutKyc;
import mr.foncier_api.enums.StatutMandat;
import mr.foncier_api.enums.StatutMissionTerrain;
import mr.foncier_api.enums.TypeBien;
import mr.foncier_api.enums.TypeMandat;
import mr.foncier_api.enums.TypeMissionTerrain;
import mr.foncier_api.enums.TypeTransactionBien;
import mr.foncier_api.repository.BienRepository;
import mr.foncier_api.repository.MandatRepository;
import mr.foncier_api.repository.MissionTerrainRepository;
import mr.foncier_api.repository.UtilisateurRepository;
import mr.foncier_api.service.GeoUtils;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;

/**
 * Jeu de données de démonstration pour l'intégration Frontend (Checklist #15 : 10 biens avec photos, rôles, mandat & mission).
 * S'exécute de manière idempotente si app.seed.enabled=true.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DevDataSeeder {

    @Value("${app.seed.enabled:true}")
    private boolean seedEnabled;

    private final BienRepository bienRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final MandatRepository mandatRepository;
    private final MissionTerrainRepository missionTerrainRepository;

    @Order(2)
    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() {
        if (!seedEnabled) {
            return;
        }

        Utilisateur proprio1 = getOrCreateUser("+22236100001", "Ould Sidi", "Mohamed", RoleUtilisateur.proprietaire, "premium");
        Utilisateur proprio2 = getOrCreateUser("+22236100002", "Mint Abdallahi", "Mariem", RoleUtilisateur.proprietaire, "vip");
        Utilisateur adminUser = getOrCreateUser("+22236100003", "Ba", "Amadou", RoleUtilisateur.admin, "vip");
        Utilisateur fieldAgent = getOrCreateUser("+22236100004", "Ould Brahim", "Sidi", RoleUtilisateur.agent_terrain, "premium");

        Bien firstBien = null;

        if (bienRepository.countByStatutAndEstArchiveFalse(StatutBien.publie) == 0) {
            log.info("Initialisation du jeu de données de démonstration MPX (10 biens publiés en Mauritanie)...");

            firstBien = seedBien(proprio2, TypeBien.maison, TypeTransactionBien.vente,
                    "Villa moderne R+1 avec piscine à Tevragh Zeina",
                    "Magnifique villa titrée de 5 chambres avec jardin arboré, piscine et garage double dans le quartier diplomatique.",
                    "فيلا حديثة مع مسبح وحديقة في تفرغ زينة",
                    "38000000", "480", 7, 5, 4, "Ilot K, Rue 42-015", "Nouakchott", "Tevragh Zeina",
                    18.0985, -15.9780, true, true,
                    new String[]{"https://images.unsplash.com/photo-1613490493576-7fde63acd811?w=800"});

            seedBien(proprio1, TypeBien.terrain, TypeTransactionBien.vente,
                    "Terrain titré 600 m² à Sukuk / Tevragh Zeina",
                    "Parcelle d'angle viabilisée avec titre foncier définitif, idéale pour résidence de haut standing.",
                    "قطعة أرض مسجلة بمساحة 600 متر مربع في صكوك",
                    "18500000", "600", null, null, null, "Zone Sukuk, Lot 114", "Nouakchott", "Sukuk",
                    18.1210, -15.9865, true, true,
                    new String[]{"https://images.unsplash.com/photo-1500382017468-9049fed747ef?w=800"});

            seedBien(proprio1, TypeBien.appartement, TypeTransactionBien.location,
                    "Appartement F4 haut standing meublé — Ilot C",
                    "Appartement lumineux au 3e étage avec ascenseur, groupe électrogène et sécurité 24h/24.",
                    "شقة مفروشة فاخرة في إيلو C",
                    "45000", "165", 4, 3, 2, "Ilot C, Résidence Al Manar", "Nouakchott", "Tevragh Zeina",
                    18.0890, -15.9740, true, false,
                    new String[]{"https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?w=800"});

            seedBien(proprio2, TypeBien.bureau, TypeTransactionBien.location,
                    "Plateau de bureaux 250 m² au Centre-Ville Ksar",
                    "Espace professionnel climatisé avec salle de réunion, fibre optique et parking privé.",
                    "مكاتب مهنية بمساحة 250 متر مربع في لكصر",
                    "65000", "250", 6, 0, 2, "Avenue Gamal Abdel Nasser", "Nouakchott", "Ksar",
                    18.0865, -15.9610, true, false,
                    new String[]{"https://images.unsplash.com/photo-1497366216548-37526070297c?w=800"});

            seedBien(proprio1, TypeBien.maison, TypeTransactionBien.vente,
                    "Maison familiale 4 chambres à Arafat Carrefour Madrid",
                    "Maison spacieuse proche des axes principaux, cour intérieure et dépendance.",
                    "منزل عائلي من 4 غرف في عرفات",
                    "9500000", "300", 5, 4, 3, "Secteur 3, Arafat", "Nouakchott", "Arafat",
                    18.0460, -15.9625, true, false,
                    new String[]{"https://images.unsplash.com/photo-1568605114967-8130f3a36994?w=800"});

            seedBien(proprio1, TypeBien.commercial, TypeTransactionBien.location,
                    "Local commercial sur axe passant à Sebkha",
                    "Boutique de 90 m² avec mezzanine en façade principale, fort passage commercial.",
                    "محل تجاري على الشارع الرئيسي في السبخة",
                    "30000", "90", 2, 0, 1, "Marché Capitale Sud", "Nouakchott", "Sebkha",
                    18.0740, -15.9890, false, false,
                    new String[]{"https://images.unsplash.com/photo-1441986300917-64674bd600d8?w=800"});

            seedBien(proprio2, TypeBien.appartement, TypeTransactionBien.colocation,
                    "Colocation étudiante / jeunes cadres à Teyarett",
                    "Grand appartement de 3 chambres avec cuisine équipée et connexion haut débit incluse.",
                    "سكن مشترك من 3 غرف في تيارت",
                    "12000", "120", 4, 3, 2, "Carrefour Teyarett", "Nouakchott", "Teyarett",
                    18.1180, -15.9380, false, false,
                    new String[]{"https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?w=800"});

            seedBien(proprio1, TypeBien.terrain, TypeTransactionBien.vente,
                    "Terrain résidentiel 400 m² à Riyad PK10",
                    "Terrain plat borné avec permis d'occuper régularisé dans une zone en pleine expansion.",
                    "قطعة أرض سكنية 400 متر مربع في الرياض",
                    "3200000", "400", null, null, null, "PK10 Nord", "Nouakchott", "Riyad",
                    18.0110, -15.9550, true, false,
                    new String[]{"https://images.unsplash.com/photo-1513880989635-6eb491ce7f5b?w=800"});

            seedBien(proprio2, TypeBien.maison, TypeTransactionBien.vente,
                    "Villa vue mer à Cansado — Nouadhibou",
                    "Superbe villa résidentielle à Cansado avec terrasse panoramique et titre foncier.",
                    "فيلا مطلة على البحر في كانصادو نواذيبو",
                    "22000000", "420", 6, 4, 3, "Cité Cansado", "Nouadhibou", "Cansado",
                    20.8530, -17.0340, true, true,
                    new String[]{"https://images.unsplash.com/photo-1580587771525-78b9dba3b914?w=800"});

            seedBien(adminUser, TypeBien.commercial, TypeTransactionBien.vente,
                    "Entrepôt & hangar logistique Zone Franche Nouadhibou",
                    "Bâtiment industriel de 1200 m² proche du port autonome, accès poids lourds.",
                    "مستودع لوجستي في المنطقة الحرة بنواذيبو",
                    "45000000", "1200", 3, 0, 2, "Zone Portuaire Nord", "Nouadhibou", "Numerowatt",
                    20.9160, -17.0510, true, true,
                    new String[]{"https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?w=800"});

            log.info("Seed MPX terminé : 10 biens immobiliers publiés avec coordonnées PostGIS.");
        }

        if (firstBien == null) {
            firstBien = bienRepository.findByProprietaireIdAndEstArchiveFalseOrderByCreeLeDesc(
                    proprio2.getId(),
                    org.springframework.data.domain.PageRequest.of(0, 1)
            ).stream().findFirst().orElse(null);
        }

        if (firstBien != null) {
            seedDemoMandatAndMissionIfMissing(firstBien, proprio2, fieldAgent);
        }
    }

    private void seedDemoMandatAndMissionIfMissing(Bien bien, Utilisateur owner, Utilisateur agent) {
        if (!mandatRepository.existsByBienIdAndStatutAndEstArchiveFalse(bien.getId(), StatutMandat.actif)) {
            Long seqMandat = mandatRepository.getNextReferenceSequenceValue();
            String refMandat = String.format("MAN-%d-%05d", Year.now().getValue(), seqMandat);
            Mandat demoMandat = Mandat.builder()
                    .reference(refMandat)
                    .type(TypeMandat.exclusif)
                    .bien(bien)
                    .proprietaire(owner)
                    .agent(agent)
                    .tauxCommission(new BigDecimal("5.00"))
                    .dateDebut(LocalDate.now())
                    .dateFin(LocalDate.now().plusMonths(6))
                    .statut(StatutMandat.actif)
                    .conditionsParticulieres("Mandat exclusif de démonstration MPX")
                    .build();
            demoMandat = mandatRepository.save(demoMandat);
            bien.setMandatId(demoMandat.getId());
            bienRepository.save(bien);
        }

        if (!missionTerrainRepository.existsByBienIdAndAgentId(bien.getId(), agent.getId())) {
            Long seqMission = missionTerrainRepository.getNextReferenceSequenceValue();
            String refMission = String.format("MIS-%d-%05d", Year.now().getValue(), seqMission);
            MissionTerrain demoMission = MissionTerrain.builder()
                    .reference(refMission)
                    .bien(bien)
                    .agent(agent)
                    .type(TypeMissionTerrain.verification_physique)
                    .statut(StatutMissionTerrain.assignee)
                    .datePlanifiee(Instant.now().plusSeconds(86400))
                    .build();
            missionTerrainRepository.save(demoMission);
        }
    }

    private Utilisateur getOrCreateUser(
            String telephone,
            String nom,
            String prenom,
            RoleUtilisateur role,
            String plan
    ) {
        return utilisateurRepository.findByTelephone(telephone)
                .orElseGet(() -> utilisateurRepository.save(
                        Utilisateur.builder()
                                .telephone(telephone)
                                .nom(nom)
                                .prenom(prenom)
                                .role(role)
                                .roles(new String[]{role.name()})
                                .plan(plan)
                                .langue(Langue.fr)
                                .kycVerifie(true)
                                .statutKyc(StatutKyc.verifie)
                                .estActif(true)
                                .build()
                ));
    }

    private Bien seedBien(
            Utilisateur owner,
            TypeBien type,
            TypeTransactionBien transaction,
            String titre,
            String descFr,
            String descAr,
            String prix,
            String surface,
            Integer pieces,
            Integer chambres,
            Integer sdb,
            String adresse,
            String ville,
            String quartier,
            double lat,
            double lng,
            boolean verifie,
            boolean vip,
            String[] photos
    ) {
        Long seq = bienRepository.getNextReferenceSequenceValue();
        String ref = String.format("FM-%d-%05d", Year.now().getValue(), seq);
        Point exact = GeoUtils.createPoint(lat, lng);
        Point fuzzy = GeoUtils.generateFuzzyPoint(exact, 250);

        Bien bien = Bien.builder()
                .reference(ref)
                .titre(titre)
                .descriptionFr(descFr)
                .descriptionAr(descAr)
                .type(type)
                .transaction(transaction)
                .prix(new BigDecimal(prix))
                .devise("MRU")
                .superficieM2(new BigDecimal(surface))
                .nbPieces(pieces)
                .nbChambres(chambres)
                .nbSdb(sdb)
                .adresse(adresse)
                .ville(ville)
                .quartierNom(quartier)
                .positionExacte(exact)
                .positionFloue(fuzzy)
                .rayonFlouM(250)
                .photos(photos)
                .statut(StatutBien.publie)
                .estVerifie(verifie)
                .estVip(vip)
                .publieLe(Instant.now())
                .proprietaire(owner)
                .build();
        return bienRepository.save(bien);
    }
}
