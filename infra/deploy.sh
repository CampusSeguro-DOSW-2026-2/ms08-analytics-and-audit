#!/usr/bin/env bash
# Deploys MS08 on its VM: stack ms08-<environment> behind the edge Caddy, and checks the deployment.
# The self-hosted runner runs it from .github/workflows/deploy.yml with the variables and secrets of the
# GitHub Environment ms08-<environment>. It is idempotent: running it again with another TAG is the rollback.
# The edge (Caddy with mTLS) is started once by hand, with the "Conexión al API Gateway" guide of the wiki.
set -euo pipefail

falta() { echo "::error::Falta la variable $1 (Environment ms08-${ENTORNO:-?} en GitHub)" >&2; exit 1; }
for VAR in ENTORNO MONGODB_URI; do
    [ -n "${!VAR:-}" ] || falta "$VAR"
done
[[ "$ENTORNO" =~ ^(test|prod)$ ]] || { echo "::error::ENTORNO debe ser test o prod" >&2; exit 1; }

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
# Same tag that the CI publishes: sha- + the first 7 characters of the commit.
TAG="${TAG:-sha-$(git -C "$REPO_DIR" rev-parse HEAD | cut -c1-7)}"
[[ "$TAG" =~ ^sha-[0-9a-f]{7}$ ]] || { echo "::error::TAG debe tener la forma sha-xxxxxxx (recibido: $TAG)" >&2; exit 1; }
IMAGE="${IMAGE:-ghcr.io/campusseguro-dosw-2026-2/ms08-analytics-and-audit-api}"
DEPLOY_DIR="${DEPLOY_DIR:-$HOME/campusseguro/ms08}"
PUERTO=$([ "$ENTORNO" = prod ] && echo 443 || echo 8443)
STACK_DIR="$DEPLOY_DIR/$ENTORNO"

docker network inspect campus-edge >/dev/null 2>&1 || {
    echo "::error::No existe la red campus-edge: levante primero el borde Caddy de la VM (guía Conexión al API Gateway)" >&2
    exit 1
}

# One-line values for the .env (single quotes, no interpolation). The connection string has a password,
# so it is never printed.
linea_env() {
    local valor="$2"
    [[ "$valor" != *"'"* && "$valor" != *$'\n'* ]] || { echo "::error::$1 no puede contener comillas simples ni saltos de línea" >&2; exit 1; }
    printf "%s='%s'\n" "$1" "$valor"
}

umask 077
mkdir -p "$STACK_DIR"

echo "== Archivos del despliegue ($ENTORNO, $TAG) en $STACK_DIR"
cp "$REPO_DIR/infra/docker-compose.prod.yml" "$STACK_DIR/docker-compose.yml"

{
    linea_env ENTORNO "$ENTORNO"
    linea_env IMAGE "$IMAGE"
    linea_env TAG "$TAG"
    linea_env MONGODB_URI "$MONGODB_URI"
    linea_env LOG_LEVEL "${LOG_LEVEL:-INFO}"
    linea_env AUDIT_TTL_INDEX_ENABLED "${AUDIT_TTL_INDEX_ENABLED:-true}"
    linea_env AUDIT_CLEANUP_JOB_ENABLED "${AUDIT_CLEANUP_JOB_ENABLED:-false}"
} > "$STACK_DIR/.env"
printf '%s\n' "${MS08_EXTRA_ENV:-}" > "$STACK_DIR/extra.env"

echo "== Imagen $IMAGE:$TAG"
# On the VM the runner provides GHCR_USER/GHCR_TOKEN; PULL_POLICY=missing is only to test with a local image.
if [ -n "${GHCR_TOKEN:-}" ]; then
    printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u "${GHCR_USER:?Falta GHCR_USER}" --password-stdin >/dev/null
fi
C=(docker compose --project-directory "$STACK_DIR")
"${C[@]}" config --quiet
"${C[@]}" up -d --remove-orphans --pull "${PULL_POLICY:-always}"
[ -z "${GHCR_TOKEN:-}" ] || docker logout ghcr.io >/dev/null

# The firewall and mTLS prevent testing from outside: it is tested inside the VM, through the campus-edge network.
echo "== Verificación de http://ms08-$ENTORNO:8088/actuator/health (espera hasta 3 min: arranque de MS08 y conexión a la base)"
CURL=(docker run --rm --network campus-edge curlimages/curl:8.11.1 -s -o /dev/null -w '%{http_code}')
for _ in $(seq 1 36); do
    if [ "$("${CURL[@]}" "http://ms08-$ENTORNO:8088/actuator/health" || true)" = 200 ]; then
        # Without the GW certificate the edge must cut the connection in TLS (curl 35, 55 or 56), never answer.
        # Another code (0 = it answered, 7 = nobody listens) means the edge is missing or does not ask for the certificate.
        RC=0; curl -sk -o /dev/null "https://127.0.0.1:$PUERTO/actuator/health" || RC=$?
        [[ "$RC" =~ ^(35|55|56)$ ]] || {
            echo "::error::El borde :$PUERTO no rechazó la conexión sin certificado de cliente (curl terminó con $RC)"
            exit 1
        }
        echo "Despliegue verificado: health 200 y borde :$PUERTO exige el certificado del GW ($ENTORNO, $TAG)"
        exit 0
    fi
    sleep 5
done
echo "::error::MS08 no respondió 200 en /actuator/health. Revise: docker compose --project-directory $STACK_DIR logs"
"${C[@]}" logs --tail=50 ms08 || true
exit 1
