# Guide de Configuration de la Base de Données — Foncier Mauritanie (MPX)

> **SGBD requis :** PostgreSQL **16+** (recommandé : **PostgreSQL 17.x**)  
> **Extension géospatiale :** PostGIS **3.4+** (recommandé : **PostGIS 3.5.x**)  
> **Schéma applicatif :** Schéma unique **`public`** (11 tables essentielles)  
> **Encodage :** `UTF8` · **Système géodésique :** `WGS84` (`SRID 4326`)

Ce document décrit les prérequis logiciels, les extensions et les requêtes SQL à exécuter sur PostgreSQL **avant le démarrage du backend Spring Boot** (`foncier-api`), ainsi que les initialisations automatiques exécutées par l'application au lancement.

---

## Sommaire

1. [Prérequis Logiciels & Installation de PostGIS](#1-prérequis-logiciels--installation-de-postgis)
2. [Requêtes SQL à exécuter AVANT le démarrage du Backend](#2-requêtes-sql-à-exécuter-avant-le-démarrage-du-backend)
3. [Requêtes SQL de vérification (Checklist pré-démarrage)](#3-requêtes-sql-de-vérification-checklist-pré-démarrage)
4. [Ce que le Backend initialise automatiquement au démarrage](#4-ce-que-le-backend-initialise-automatiquement-au-démarrage)
5. [Les 11 Tables Essentielles du Schéma `public`](#5-les-11-tables-essentielles-du-schéma-public)
6. [Variables d'environnement & Connexion Spring Boot](#6-variables-denvironnement--connexion-spring-boot)
7. [Configuration d'un utilisateur SQL dédié (Pré-production / Production)](#7-configuration-dun-utilisateur-sql-dédié-pré-production--production)
8. [Alternative rapide avec Docker](#8-alternative-rapide-avec-docker)
9. [Résolution des erreurs courantes (Troubleshooting)](#9-résolution-des-erreurs-courantes-troubleshooting)

---

## 1. Prérequis Logiciels & Installation de PostGIS

L'instruction SQL `CREATE EXTENSION "postgis";` ne fonctionne que si les **bibliothèques binaires de PostGIS** (`GEOS`, `PROJ`, `GDAL`, `postgis-3.dll` / `postgis-3.so`) sont installées sur la machine hébergeant PostgreSQL.

| Système | Méthode d'installation des binaires PostgreSQL + PostGIS |
| :--- | :--- |
| **Windows** | 1. Installer **PostgreSQL 17** (EDB Installer).<br>2. Lancer **Application Stack Builder** -> sélectionner l'instance sur le port `5432` -> *Spatial Extensions* -> cocher **PostGIS 3.5 Bundle for PostgreSQL 17** *(ou utiliser l'installeur OSGeo `postgis-bundle-pg17x64-setup-*.exe`)*.<br>⚠️ **Vérifier le dossier cible** : il doit pointer vers le répertoire exact du `postgres.exe` actif sur le port `5432`. |
| **Ubuntu / Debian** | `sudo apt update && sudo apt install -y postgresql-17 postgresql-17-postgis-3 postgresql-contrib` |
| **macOS (Homebrew)** | `brew install postgresql@17 postgis` |
| **Docker** | Utiliser directement l'image officielle `postgis/postgis:17-3.5` (voir [Section 8](#8-alternative-rapide-avec-docker)). |

---

## 2. Requêtes SQL à exécuter AVANT le démarrage du Backend

### Étape 2.1 — Créer la base de données `foncier_api`

Connectez-vous au serveur PostgreSQL avec un compte **super-utilisateur** (par défaut `postgres`) via `psql` ou **pgAdmin 4** :

```powershell
psql -h localhost -p 5432 -U postgres
```

Exécutez la création de la base en `UTF8` :

```sql
CREATE DATABASE foncier_api
    WITH OWNER = postgres
    ENCODING = 'UTF8'
    TEMPLATE = template0;
```

---

### Étape 2.2 — Activer les extensions et la séquence métier dans `public`

> **Attention :** Assurez-vous d'être connecté **à l'intérieur de la base `foncier_api`** (et non dans la base système `postgres`) avant d'exécuter le script suivant !

Sous `psql` :
```sql
\c foncier_api
```
*(Dans **pgAdmin 4** : clic droit sur la base **`foncier_api`** -> **Query Tool**).*

Exécutez ensuite ces 4 instructions SQL :

```sql
-- ===================================================================
-- 1. EXTENSIONS POSTGRESQL OBLIGATOIRES (requiert les droits SUPERUSER)
-- ===================================================================

-- Génération d'identifiants UUID v4
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Moteur géospatial : type geography(Point, 4326), ST_DWithin, ST_Distance, index GIST
CREATE EXTENSION IF NOT EXISTS "postgis";

-- Recherche plein texte tolérante par trigrammes : opérateur gin_trgm_ops
CREATE EXTENSION IF NOT EXISTS "pg_trgm";


-- ===================================================================
-- 2. SÉQUENCE ATOMIQUE POUR LES RÉFÉRENCES DES BIENS (EX-036)
-- Format généré par le backend : FM-AAAA-NNNNN (ex: FM-2026-00001)
-- ===================================================================

CREATE SEQUENCE IF NOT EXISTS public.seq_bien_reference
    START WITH 1
    INCREMENT BY 1;
```

### Pourquoi chaque extension est-elle requise ?

| Extension | Rôle technique dans `foncier-api` |
| :--- | :--- |
| **`uuid-ossp`** | Support natif des identifiants `UUID` dans PostgreSQL. |
| **`postgis`** | Stockage des coordonnées GPS exactes (`position_exacte`) et floutées à 300 m (`position_floue`) au format `geography(Point, 4326)`, calcul de distance réelle en mètres/kilomètres (`ST_DWithin`, `ST_Distance`) sur `/v1/properties/nearby` et `/v1/properties`. |
| **`pg_trgm`** | Indexation trigramme `GIN (titre gin_trgm_ops)` accélérant la recherche textuelle (`ILIKE '%...%'`) sur les titres d'annonces. |

---

## 3. Requêtes SQL de vérification (Checklist pré-démarrage)

Avant de lancer le backend Spring Boot, exécutez ces 3 requêtes dans `foncier_api` pour valider que l'environnement est prêt :

```sql
-- 1. Doit retourner les 4 lignes : pg_trgm, plpgsql, postgis, uuid-ossp
SELECT extname, extversion
FROM pg_extension
WHERE extname IN ('plpgsql', 'uuid-ossp', 'postgis', 'pg_trgm')
ORDER BY extname;

-- 2. Doit afficher la version complète de PostGIS (3.4+ / 3.5+)
SELECT PostGIS_Full_Version();

-- 3. Doit retourner 1 ligne : seq_bien_reference dans public
SELECT schemaname, sequencename, start_value, increment_by
FROM pg_sequences
WHERE schemaname = 'public' AND sequencename = 'seq_bien_reference';
```

---

## 4. Ce que le Backend initialise automatiquement au démarrage

Une fois la base `foncier_api` prête, le lancement de l'application (`.\mvnw.cmd spring-boot:run`) enchaîne automatiquement **5 étapes dans cet ordre précis** :

1. **Exécution de `src/main/resources/schema.sql`** (`spring.sql.init.mode=always`) :
   - Vérifie de manière idempotente (`IF NOT EXISTS`) les 3 extensions et la séquence `public.seq_bien_reference` avant l'initialisation JPA.
2. **Création / Mise à jour des 11 tables JPA par Hibernate** (`spring.jpa.hibernate.ddl-auto=update`) :
   - Crée automatiquement les 11 tables applicatives dans le schéma **`public`**, les index standards (`idx_bien_reference`, `idx_bien_statut_type_prix`, `idx_utilisateur_telephone`...) et les colonnes `geography(Point,4326)`.
3. **Initialisation des tables de session HTTP** (`spring.session.jdbc.initialize-schema=always`) :
   - Crée automatiquement `SPRING_SESSION` et `SPRING_SESSION_ATTRIBUTES` dans `public`.
4. **Création des index spatiaux et plein texte (`SpatialIndexInitializer`, `@Order(1)`)** :
   - Exécute automatiquement après la création de la table `public.bien` :
     ```sql
     CREATE INDEX IF NOT EXISTS idx_bien_position_floue_gist ON public.bien USING GIST (position_floue);
     CREATE INDEX IF NOT EXISTS idx_bien_position_exacte_gist ON public.bien USING GIST (position_exacte);
     CREATE INDEX IF NOT EXISTS idx_bien_titre_trgm ON public.bien USING GIN (titre gin_trgm_ops);
     ```
5. **Injection des données de démonstration (`DevDataSeeder`, `@Order(2)`)** :
   - Si `SEED_ENABLED=true` (actif par défaut en développement) et qu'aucun bien publié n'est présent en base, insère automatiquement **3 utilisateurs de démonstration** et **10 annonces immobilières publiées** (Nouakchott & Nouadhibou) avec coordonnées GPS floutées.

---

## 5. Les 11 Tables Essentielles du Schéma `public`

Toutes les entités métier résident dans le schéma unique `public` et correspondent directement aux modules du Contrat API Frontend (v1) :

| Table (`public.*`) | Entité Java (`mr.foncier_api.entity`) | Module Contrat API |
| :--- | :--- | :--- |
| `utilisateur` | `Utilisateur` | §2 Authentification OTP & §3 Profils / Rôles / KYC |
| `bien` | `Bien` | §4 Catalogue immobilier, PostGIS & Photos |
| `favori` | `Favori` | §4 Favoris utilisateur (`/users/me/favorites`) |
| `document` | `Document` | §3 Pièces KYC & §5 Documents fonciers scellés SHA-256 |
| `mandat` | `Mandat` | §6 Mandats propriétaire / agent (`/mandats`) |
| `mission_terrain` | `MissionTerrain` | §7 Missions de vérification terrain (`/missions`) |
| `deverrouillage` | `Deverrouillage` | §8 Déverrouillage des coordonnées propriétaire (`/properties/:id/unlock`) |
| `notification` | `Notification` | §9 Notifications in-app (`/notifications`) |
| `formule_abonnement` | `FormuleAbonnement` | §10 Plans d'abonnement & quotas (`/plans`) |
| `abonnement` | `Abonnement` | §10 Souscriptions actives (`/subscriptions`) |
| `transaction_financiere` | `TransactionFinanciere` | §10 Factures & paiements Mobile Money / Carte (`/users/me/invoices`) |

---

## 6. Fichier `.env`, Variables d'environnement & Connexion Spring Boot

Le fichier `src/main/resources/application.properties` importe automatiquement le fichier **`.env`** situé à la racine du projet grâce à la directive native Spring Boot :

```properties
spring.config.import=optional:file:.env[.properties]
```

* **`.env`** : fichier local actif contenant vos identifiants et clés (ignoré par Git via `.gitignore`).
* **`.env.example`** : modèle versionné prêt à copier (`Copy-Item .env.example .env` sous PowerShell ou `cp .env.example .env` sous Linux/macOS).

Principales variables configurables dans `.env` (ou via variables d'environnement système en CI/Production) :

| Variable `.env` | Valeur par défaut (Dev) | Rôle |
| :--- | :--- | :--- |
| `SERVER_PORT` | `8080` | Port HTTP d'écoute du serveur Spring Boot |
| `DB_HOST` | `localhost` | Hôte du serveur PostgreSQL |
| `DB_PORT` | `5432` | Port TCP PostgreSQL |
| `DB_NAME` | `foncier_api` | Nom de la base de données |
| `DB_USERNAME` | `postgres` | Utilisateur SQL de connexion |
| `DB_PASSWORD` | `admin` | Mot de passe SQL |
| `PUBLIC_BASE_URL` | `http://localhost:8080` | URL publique de base pour les médias téléversés (`UPLOAD_DIR`) |
| `JWT_SECRET` | *(clé dev 256+ bits)* | Clé secrète HMAC-SHA256 de signature des tokens JWT |
| `OTP_DEV_MODE` | `true` | Active (`true`) le code OTP fixe `OTP_DEV_CODE` en développement |
| `SEED_ENABLED` | `true` | Active (`true`) ou désactive (`false`) l'insertion des données de démonstration au démarrage |

Exemple de lancement PowerShell (les valeurs de `.env` sont chargées automatiquement, et toute variable système les surcharge) :

```powershell
# Optionnel : surcharger ponctuellement en ligne de commande
$env:DB_PASSWORD="votre_mot_de_passe"
.\mvnw.cmd spring-boot:run
```

---

## 7. Configuration d'un utilisateur SQL dédié (Pré-production / Production)

En pré-production et en production, l'application Spring Boot **ne doit pas** se connecter avec le super-utilisateur `postgres`. Comme la création d'extensions (`postgis`, `uuid-ossp`, `pg_trgm`) requiert les droits superuser, la procédure recommandée est la suivante :

1. Exécuter les requêtes de la **[Section 2](#2-requêtes-sql-à-exécuter-avant-le-démarrage-du-backend)** avec le compte `postgres`.
2. Créer un rôle applicatif dédié `foncier_app` et lui accorder les privilèges uniquement sur le schéma `public` :

```sql
-- Connecté à foncier_api en tant que superuser (postgres)
CREATE ROLE foncier_app WITH LOGIN PASSWORD 'MotDePasseRobusteProd2026!';

GRANT CONNECT ON DATABASE foncier_api TO foncier_app;

-- Droits sur le schéma unique public
GRANT USAGE, CREATE ON SCHEMA public TO foncier_app;

-- Droits sur les tables et séquences existantes et futures dans public
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO foncier_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO foncier_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO foncier_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO foncier_app;
```

3. Désactiver le seed de démonstration en production (`SEED_ENABLED=false`).

---

## 8. Alternative rapide avec Docker

Si vous préférez ne pas installer PostgreSQL 17 et PostGIS 3.5 directement sur votre poste, vous pouvez démarrer une instance prête à l'emploi en une commande :

```powershell
docker run --name foncier-postgres `
  -e POSTGRES_USER=postgres `
  -e POSTGRES_PASSWORD=admin `
  -e POSTGRES_DB=foncier_api `
  -p 5432:5432 `
  -d postgis/postgis:17-3.5
```

Avec l'image officielle `postgis/postgis:17-3.5`, l'extension `postgis` est pré-activée dans `foncier_api` et `schema.sql` activera automatiquement `uuid-ossp`, `pg_trgm` et la séquence `seq_bien_reference` dès le premier démarrage du backend.

---

## 9. Résolution des erreurs courantes (Troubleshooting)

| Message d'erreur au démarrage | Cause probable | Solution |
| :--- | :--- | :--- |
| `ERROR: extension "postgis" is not available` ou `could not open extension control file ".../postgis.control"` | Les binaires PostGIS ne sont pas installés dans le répertoire du serveur PostgreSQL actif. | Installer le bundle PostGIS correspondant à la version majeure de PostgreSQL (ex. PostGIS 3.5 pour PG 17) dans le dossier exact où s'exécute `postgres.exe`. |
| `Connection to localhost:5432 refused` | Le serveur PostgreSQL est arrêté ou écoute sur un autre port (ex. `5433`). | Démarrer le service Windows PostgreSQL (`Start-Service postgresql-x64-17`) ou via `pg_ctl start -D "<dossier_data>"`, et vérifier le port avec `pg_isready -h localhost -p 5432`. |
| `ERROR: permission denied to create extension "postgis"` | Le compte configuré dans `DB_USERNAME` n'est pas `SUPERUSER` et les extensions n'ont pas encore été créées. | Exécuter une fois le script SQL de la **[Section 2.2](#étape-22--activer-les-extensions-et-la-séquence-métier-dans-public)** avec le super-utilisateur `postgres`. |
| `ERROR: type "geography" does not exist` | L'extension `postgis` n'est pas activée dans la base `foncier_api` ou dans le `search_path`. | Exécuter `CREATE EXTENSION IF NOT EXISTS "postgis" SCHEMA public;` dans la base `foncier_api`. |
