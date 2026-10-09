#!/usr/bin/env bash
# ============================================================
# Prepara un servidor Ubuntu nuevo (ej: Oracle Cloud Always Free) para GGaming.
# Correr UNA vez:  bash scripts/preparar-servidor.sh
# Hace: actualizaciones automáticas de seguridad, fail2ban (frena ataques a SSH),
#       firewall (solo SSH, HTTP y HTTPS), Docker y sqlite3 (para los backups).
# ============================================================
set -euo pipefail

echo "==> Actualizando el sistema"
sudo apt-get update -y
sudo DEBIAN_FRONTEND=noninteractive apt-get upgrade -y

echo "==> Actualizaciones de seguridad automáticas"
sudo apt-get install -y unattended-upgrades
sudo dpkg-reconfigure -f noninteractive unattended-upgrades

echo "==> fail2ban: bloquea IPs que prueban contraseñas por SSH"
sudo apt-get install -y fail2ban
sudo systemctl enable --now fail2ban

echo "==> SSH solo con llave (sin contraseñas)"
sudo sed -i 's/^#\?PasswordAuthentication .*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo sed -i 's/^#\?PermitRootLogin .*/PermitRootLogin no/' /etc/ssh/sshd_config
sudo systemctl restart ssh || sudo systemctl restart sshd

echo "==> Firewall del sistema: abrir solo 80 y 443 (el 22 de SSH ya está abierto)"
# Las imágenes de Ubuntu de Oracle traen reglas de iptables propias: se agregan ahí.
sudo apt-get install -y iptables-persistent
for puerto in 80 443; do
  sudo iptables -C INPUT -p tcp --dport "$puerto" -j ACCEPT 2>/dev/null \
    || sudo iptables -I INPUT 5 -p tcp --dport "$puerto" -j ACCEPT
done
sudo iptables -C INPUT -p udp --dport 443 -j ACCEPT 2>/dev/null || sudo iptables -I INPUT 5 -p udp --dport 443 -j ACCEPT
sudo netfilter-persistent save

echo "==> Docker"
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
fi

echo "==> sqlite3 (para los backups)"
sudo apt-get install -y sqlite3

echo ""
echo "Listo. Cerrá la sesión SSH y volvé a entrar para usar docker sin sudo."
