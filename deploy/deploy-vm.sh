#!/usr/bin/env bash
set -euo pipefail

STAGING_DIR="/tmp/air-alert-deploy"
APP_DIR="/opt/air-alert"
CONF_DIR="/etc/air-alert"
SERVICE_NAME="air-alert-backend"
HEALTH_URL="http://127.0.0.1:8080/api/v1/regions"

echo "==> Ensuring Java 17 JRE and curl are installed..."
if ! command -v java >/dev/null 2>&1; then
    sudo apt-get update -qq
    sudo apt-get install -y -qq openjdk-17-jre-headless curl
fi

echo "==> Ensuring 1.5 GB swap exists (protects e2-micro 1 GB RAM)..."
if [ "$(swapon --show | wc -l)" -eq 0 ] && [ ! -f /swapfile ]; then
    sudo fallocate -l 1536M /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    if ! grep -q '^/swapfile ' /etc/fstab; then
        echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
    fi
fi

echo "==> Ensuring dedicated service user and directories exist..."
if ! id -u airalert >/dev/null 2>&1; then
    sudo useradd --system --no-create-home --shell /usr/sbin/nologin airalert
fi
sudo mkdir -p "${APP_DIR}" "${CONF_DIR}"

if [ ! -f "${CONF_DIR}/backend.env" ]; then
    echo "==> Creating placeholder ${CONF_DIR}/backend.env (update with real tokens!)"
    sudo install -m 0640 -o root -g airalert "${STAGING_DIR}/backend.env.example" "${CONF_DIR}/backend.env"
fi

echo "==> Installing new backend JAR..."
if [ -f "${APP_DIR}/air-alert-backend.jar" ]; then
    sudo cp "${APP_DIR}/air-alert-backend.jar" "${APP_DIR}/air-alert-backend.jar.prev"
fi
sudo install -m 0644 -o airalert -g airalert "${STAGING_DIR}/air-alert-backend.jar" "${APP_DIR}/air-alert-backend.jar.new"
sudo mv -f "${APP_DIR}/air-alert-backend.jar.new" "${APP_DIR}/air-alert-backend.jar"
sudo chown -R airalert:airalert "${APP_DIR}"
sudo chmod 0750 "${CONF_DIR}"
sudo chown root:airalert "${CONF_DIR}"
if [ -f "${CONF_DIR}/backend.env" ]; then
    sudo chown root:airalert "${CONF_DIR}/backend.env"
    sudo chmod 0640 "${CONF_DIR}/backend.env"
fi
if [ -f "${CONF_DIR}/firebase-service-account.json" ]; then
    sudo chown root:airalert "${CONF_DIR}/firebase-service-account.json"
    sudo chmod 0640 "${CONF_DIR}/firebase-service-account.json"
fi

echo "==> Installing systemd unit and restarting ${SERVICE_NAME}..."
sudo install -m 0644 "${STAGING_DIR}/air-alert-backend.service" "/etc/systemd/system/${SERVICE_NAME}.service"
sudo systemctl daemon-reload
sudo systemctl enable "${SERVICE_NAME}"
sudo systemctl restart "${SERVICE_NAME}"

echo "==> Waiting for backend health check (${HEALTH_URL})..."
for i in $(seq 1 30); do
    if curl -fsS --max-time 3 "${HEALTH_URL}" >/dev/null 2>&1; then
        echo "==> Deployment succeeded! Service is healthy."
        rm -rf "${STAGING_DIR}"
        exit 0
    fi
    sleep 2
done

echo "==> Health check failed after 60s! Recent service logs:"
sudo journalctl -u "${SERVICE_NAME}" -n 60 --no-pager || true
exit 1
