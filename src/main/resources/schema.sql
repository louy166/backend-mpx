-- ===================================================================
-- Initialisation des extensions PostgreSQL / PostGIS (schéma public)
-- ===================================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Séquence atomique pour la référence métier des biens (FM-AAAA-NNNNN, EX-036)
CREATE SEQUENCE IF NOT EXISTS public.seq_bien_reference START WITH 1 INCREMENT BY 1;

-- Séquence atomique pour les factures et transactions financières (FAC-AAAA-NNNNN, Contrat API §10)
CREATE SEQUENCE IF NOT EXISTS public.seq_transaction_reference START WITH 1 INCREMENT BY 1;

-- Séquence atomique pour la référence métier des mandats (MAN-AAAA-NNNNN, Contrat API §6)
CREATE SEQUENCE IF NOT EXISTS public.seq_mandat_reference START WITH 1 INCREMENT BY 1;

-- Séquence atomique pour la référence métier des missions terrain (MIS-AAAA-NNNNN, Contrat API §7)
CREATE SEQUENCE IF NOT EXISTS public.seq_mission_reference START WITH 1 INCREMENT BY 1;
