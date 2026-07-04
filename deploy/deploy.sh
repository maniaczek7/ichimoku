#!/bin/bash
set -euo pipefail

# ============================================================
# Ichimoku Screener — AWS EC2 Deploy Script
# ============================================================
# Usage:
#   First-time setup:  ./deploy.sh setup
#   Deploy/update:     ./deploy.sh deploy
#   Status:            ./deploy.sh status
#   Logs:              ./deploy.sh logs
# ============================================================

EC2_HOST="${EC2_HOST:-13.62.158.162}"
EC2_USER="${EC2_USER:-ec2-user}"
EC2_KEY="${EC2_KEY:-}"
REPO_URL="https://github.com/maniaczek7/ichimoku.git"
APP_DIR="/home/${EC2_USER}/ichimoku"

ssh_cmd() {
    local key_opt=""
    if [ -n "$EC2_KEY" ]; then
        key_opt="-i $EC2_KEY"
    fi
    ssh -o StrictHostKeyChecking=no $key_opt "${EC2_USER}@${EC2_HOST}" "$@"
}

cmd_setup() {
    echo "=== Setting up EC2 instance ==="

    ssh_cmd bash -s <<'REMOTE'
set -euo pipefail

echo "--- Installing Docker ---"
if ! command -v docker &>/dev/null; then
    sudo dnf install -y docker
    sudo systemctl enable --now docker
    sudo usermod -aG docker $USER
fi

echo "--- Installing Docker Compose plugin ---"
if ! docker compose version &>/dev/null; then
    COMPOSE_VERSION=$(curl -s https://api.github.com/repos/docker/compose/releases/latest | grep tag_name | cut -d'"' -f4)
    sudo mkdir -p /usr/local/lib/docker/cli-plugins
    sudo curl -SL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-$(uname -m)" \
        -o /usr/local/lib/docker/cli-plugins/docker-compose
    sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
fi

echo "--- Installing Docker Buildx ---"
if ! docker buildx version &>/dev/null; then
    BUILDX_VERSION=$(curl -s https://api.github.com/repos/docker/buildx/releases/latest | grep tag_name | cut -d'"' -f4)
    sudo mkdir -p /usr/local/lib/docker/cli-plugins
    sudo curl -SL "https://github.com/docker/buildx/releases/download/${BUILDX_VERSION}/buildx-${BUILDX_VERSION}.linux-$(uname -m)" \
        -o /usr/local/lib/docker/cli-plugins/docker-buildx
    sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-buildx
fi

echo "--- Installing git ---"
if ! command -v git &>/dev/null; then
    sudo dnf install -y git
fi

echo "--- Setting up swap (2GB) ---"
if [ ! -f /swapfile ]; then
    sudo fallocate -l 2G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile swap swap defaults 0 0' | sudo tee -a /etc/fstab
fi

echo "--- Cloning repository ---"
if [ ! -d "$HOME/ichimoku" ]; then
    git clone https://github.com/maniaczek7/ichimoku.git "$HOME/ichimoku"
fi

echo "--- Building and starting app ---"
cd "$HOME/ichimoku"
sudo docker compose up -d --build 2>&1 | tail -20
sudo docker compose ps

echo ""
echo "=== Setup complete! ==="
echo "App: http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4)"
REMOTE
}

cmd_deploy() {
    echo "=== Deploying latest code ==="
    ssh_cmd bash -s <<REMOTE
set -euo pipefail
cd ${APP_DIR}
git fetch origin master
git reset --hard origin/master
sudo docker compose up -d --build 2>&1 | tail -20
sudo docker compose ps
echo ""
echo "=== Deploy complete! ==="
REMOTE
}

cmd_status() {
    echo "=== App status ==="
    ssh_cmd "cd ${APP_DIR} && sudo docker compose ps"
}

cmd_logs() {
    ssh_cmd "cd ${APP_DIR} && sudo docker compose logs --tail=50 -f"
}

cmd_restart() {
    echo "=== Restarting app ==="
    ssh_cmd "cd ${APP_DIR} && sudo docker compose restart && sudo docker compose ps"
}

cmd_stop() {
    echo "=== Stopping app ==="
    ssh_cmd "cd ${APP_DIR} && sudo docker compose down"
}

case "${1:-help}" in
    setup)   cmd_setup ;;
    deploy)  cmd_deploy ;;
    status)  cmd_status ;;
    logs)    cmd_logs ;;
    restart) cmd_restart ;;
    stop)    cmd_stop ;;
    *)
        echo "Usage: $0 {setup|deploy|status|logs|restart|stop}"
        echo ""
        echo "  setup   — First-time: install Docker, clone repo, build & start"
        echo "  deploy  — Pull latest code, rebuild & restart containers"
        echo "  status  — Show running containers"
        echo "  logs    — Tail container logs"
        echo "  restart — Restart containers"
        echo "  stop    — Stop all containers"
        echo ""
        echo "Environment variables:"
        echo "  EC2_HOST  — EC2 public IP (default: 13.62.158.162)"
        echo "  EC2_USER  — SSH user (default: ec2-user)"
        echo "  EC2_KEY   — Path to SSH private key"
        ;;
esac
