#!/bin/bash
# =============================================================
# Script de test API — Foncier Mauritanie (MPX)
# Usage: bash test-api.sh
# Prerequis: docker compose up -d (attendre ~30s que l'API demarre)
# =============================================================

BASE="http://localhost:8080"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'
PASS=0
FAIL=0

test_endpoint() {
  local method="$1"
  local url="$2"
  local desc="$3"
  local expected="$4"
  local data="$5"
  local token="$6"

  local headers=(-H "Content-Type: application/json" -H "Accept-Language: fr")
  if [ -n "$token" ]; then
    headers+=(-H "Authorization: Bearer $token")
  fi

  if [ "$method" = "GET" ]; then
    response=$(curl -s -w "\n%{http_code}" -X GET "${headers[@]}" "$BASE$url")
  elif [ "$method" = "POST" ]; then
    response=$(curl -s -w "\n%{http_code}" -X POST "${headers[@]}" -d "$data" "$BASE$url")
  elif [ "$method" = "PATCH" ]; then
    response=$(curl -s -w "\n%{http_code}" -X PATCH "${headers[@]}" -d "$data" "$BASE$url")
  elif [ "$method" = "PUT" ]; then
    response=$(curl -s -w "\n%{http_code}" -X PUT "${headers[@]}" -d "$data" "$BASE$url")
  fi

  code=$(echo "$response" | tail -1)
  body=$(echo "$response" | sed '$d')

  if [ "$code" = "$expected" ]; then
    echo -e "${GREEN}[PASS]${NC} $desc — HTTP $code"
    PASS=$((PASS + 1))
  else
    echo -e "${RED}[FAIL]${NC} $desc — attendu $expected, recu $code"
    echo "       Body: $(echo "$body" | head -c 200)"
    FAIL=$((FAIL + 1))
  fi
}

echo "=========================================="
echo " TEST API BACKEND — FONCIER MAURITANIE"
echo "=========================================="
echo ""

# --- 1. Health check ---
echo -e "${YELLOW}--- Connectivite ---${NC}"
test_endpoint "GET" "/" "Racine (404 attendu)" "404"

# --- 2. Swagger UI ---
echo ""
echo -e "${YELLOW}--- Documentation ---${NC}"
test_endpoint "GET" "/api/docs" "Swagger UI" "200"
test_endpoint "GET" "/v3/api-docs" "OpenAPI JSON" "200"

# --- 3. Auth Flow ---
echo ""
echo -e "${YELLOW}--- Auth OTP ---${NC}"
test_endpoint "POST" "/auth/request-otp" "Request OTP" "200" '{"telephone":"+22212345678"}'

# Verify OTP et recuperer le token
echo -n "  Verification OTP... "
auth_response=$(curl -s -X POST "$BASE/auth/verify-otp" \
  -H "Content-Type: application/json" \
  -d '{"telephone":"+22212345678","otp":"123456"}')

ACCESS_TOKEN=$(echo "$auth_response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null)
REFRESH_TOKEN=$(echo "$auth_response" | python3 -c "import sys,json; print(json.load(sys.stdin).get('refresh_token',''))" 2>/dev/null)

if [ -n "$ACCESS_TOKEN" ] && [ "$ACCESS_TOKEN" != "" ]; then
  echo -e "${GREEN}[PASS]${NC} Verify OTP — Token recu"
  PASS=$((PASS + 1))
else
  echo -e "${RED}[FAIL]${NC} Verify OTP — Pas de token"
  echo "       Response: $(echo "$auth_response" | head -c 200)"
  FAIL=$((FAIL + 1))
  echo ""
  echo -e "${RED}ARRET: Impossible de continuer sans token.${NC}"
  echo "PASS: $PASS | FAIL: $FAIL"
  exit 1
fi

# --- 4. Profil utilisateur ---
echo ""
echo -e "${YELLOW}--- Profil ---${NC}"
test_endpoint "GET" "/users/me" "Mon profil" "200" "" "$ACCESS_TOKEN"
test_endpoint "PUT" "/users/me/role" "Changer role" "200" '{"role":"proprietaire"}' "$ACCESS_TOKEN"

# --- 5. Plans (public) ---
echo ""
echo -e "${YELLOW}--- Plans (public, sans auth) ---${NC}"
test_endpoint "GET" "/plans" "Liste des plans (sans token)" "200"

# --- 6. Properties (public) ---
echo ""
echo -e "${YELLOW}--- Biens (public) ---${NC}"
test_endpoint "GET" "/properties" "Liste biens" "200"
test_endpoint "GET" "/properties/featured" "Biens en vedette" "200"

# --- 7. Properties (auth) ---
echo ""
echo -e "${YELLOW}--- Biens (auth) ---${NC}"
test_endpoint "GET" "/users/me/properties" "Mes biens" "200" "" "$ACCESS_TOKEN"

# Creer un bien
echo -n "  Creation bien... "
prop_response=$(curl -s -w "\n%{http_code}" -X POST "$BASE/properties" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{
    "titre": "Terrain Test Nouakchott",
    "type": "terrain",
    "transaction": "vente",
    "prix": 5000000,
    "devise": "MRU",
    "surface_m2": 200,
    "adresse": "Tevragh Zeina",
    "ville": "Nouakchott",
    "quartier": "Tevragh Zeina",
    "localisation": {"latitude": 18.0858, "longitude": -15.9785}
  }')
prop_code=$(echo "$prop_response" | tail -1)
prop_body=$(echo "$prop_response" | sed '$d')
PROPERTY_ID=$(echo "$prop_body" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null)

if [ "$prop_code" = "201" ] && [ -n "$PROPERTY_ID" ]; then
  echo -e "${GREEN}[PASS]${NC} Creer bien — HTTP 201 (id: $PROPERTY_ID)"
  PASS=$((PASS + 1))
else
  echo -e "${RED}[FAIL]${NC} Creer bien — HTTP $prop_code"
  echo "       Body: $(echo "$prop_body" | head -c 200)"
  FAIL=$((FAIL + 1))
fi

# Detail bien
if [ -n "$PROPERTY_ID" ]; then
  test_endpoint "GET" "/properties/$PROPERTY_ID" "Detail bien" "200"
fi

# --- 8. Missions ---
echo ""
echo -e "${YELLOW}--- Missions ---${NC}"
test_endpoint "GET" "/missions" "Liste missions" "200" "" "$ACCESS_TOKEN"

# --- 9. Mandats ---
echo ""
echo -e "${YELLOW}--- Mandats ---${NC}"
test_endpoint "GET" "/mandats" "Liste mandats" "200" "" "$ACCESS_TOKEN"

# Creer un mandat (si on a un bien)
if [ -n "$PROPERTY_ID" ]; then
  test_endpoint "POST" "/mandats" "Creer mandat" "201" "{\"bien_id\":\"$PROPERTY_ID\",\"type\":\"exclusif\",\"commission_pourcentage\":5,\"date_debut\":\"2026-11-01\",\"date_fin\":\"2027-11-01\"}" "$ACCESS_TOKEN"
fi

# Test GET /mandats/{id} — DEVRAIT ECHOUER (endpoint manquant)
echo -e "${YELLOW}  [INFO] GET /mandats/{id} — test endpoint manquant${NC}"
test_endpoint "GET" "/mandats/00000000-0000-0000-0000-000000000001" "GET mandat par ID (devrait etre 404 ou 405)" "405"

# --- 10. Notifications ---
echo ""
echo -e "${YELLOW}--- Notifications ---${NC}"
test_endpoint "GET" "/notifications" "Mes notifications" "200" "" "$ACCESS_TOKEN"
test_endpoint "POST" "/notifications/read-all" "Tout marquer lu" "200" "" "$ACCESS_TOKEN"

# --- 11. Favoris ---
echo ""
echo -e "${YELLOW}--- Favoris ---${NC}"
test_endpoint "GET" "/users/me/favorites" "Mes favoris" "200" "" "$ACCESS_TOKEN"
if [ -n "$PROPERTY_ID" ]; then
  test_endpoint "POST" "/users/me/favorites/$PROPERTY_ID" "Ajouter favori" "200" "" "$ACCESS_TOKEN"
fi

# --- 12. Abonnement & Factures ---
echo ""
echo -e "${YELLOW}--- Abonnement & Factures ---${NC}"
test_endpoint "GET" "/users/me/subscription" "Mon abonnement" "200" "" "$ACCESS_TOKEN"
test_endpoint "GET" "/users/me/invoices" "Mes factures" "200" "" "$ACCESS_TOKEN"

# --- 13. Refresh token ---
echo ""
echo -e "${YELLOW}--- Refresh & Logout ---${NC}"
if [ -n "$REFRESH_TOKEN" ]; then
  test_endpoint "POST" "/auth/refresh" "Refresh token" "200" "{\"refresh_token\":\"$REFRESH_TOKEN\"}"
fi
test_endpoint "POST" "/auth/logout" "Logout" "200" "{\"refresh_token\":\"$REFRESH_TOKEN\"}" "$ACCESS_TOKEN"

# --- 14. Test securite (sans token) ---
echo ""
echo -e "${YELLOW}--- Securite (acces refuse sans token) ---${NC}"
test_endpoint "GET" "/users/me" "Profil sans token (401)" "401"
test_endpoint "GET" "/missions" "Missions sans token (401)" "401"
test_endpoint "GET" "/mandats" "Mandats sans token (401)" "401"

# --- Resultat ---
echo ""
echo "=========================================="
echo -e " RESULTAT: ${GREEN}$PASS PASS${NC} | ${RED}$FAIL FAIL${NC}"
echo "=========================================="
