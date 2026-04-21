#!/bin/bash
set -euo pipefail

# ──────────────────────────────────────────────────────────────
# Oracle Cloud Always Free — One-click setup
# Run as: ssh opc@<VM_IP> 'bash -s' < setup-oracle.sh
# ──────────────────────────────────────────────────────────────

echo "=== [1/4] Installing Docker ==="
sudo dnf install -y dnf-utils
sudo dnf config-manager --add-repo https://download.docker.com/linux/oracle/docker-ce.repo
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker opc
echo "Docker installed."

echo "=== [2/4] Installing Git ==="
sudo dnf install -y git
echo "Git installed."

echo "=== [3/4] Firewall: opening port 80 ==="
sudo firewall-cmd --permanent --add-port=80/tcp
sudo firewall-cmd --permanent --add-port=443/tcp
sudo firewall-cmd --reload
echo "Firewall updated."

echo "=== [4/4] Done! ==="
echo ""
echo "Next steps:"
echo "  1. Log out and log back in (for docker group)"
echo "  2. Clone your repo:  git clone <REPO_URL> ichimoku"
echo "  3. cd ichimoku"
echo "  4. docker compose up -d --build"
echo "  5. Open http://<VM_PUBLIC_IP> in browser"
echo ""
echo "To see logs:    docker compose logs -f"
echo "To rebuild:     docker compose up -d --build"
echo "To stop:        docker compose down"
