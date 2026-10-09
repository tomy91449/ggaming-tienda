#!/usr/bin/env bash
# ============================================================
# Backup de GGaming: base de datos + fotos. Guarda 14 días.
# Programarlo todas las noches (ver README):
#   sudo crontab -e   →   0 4 * * * bash /home/ubuntu/ggaming-tienda/scripts/backup.sh >> /var/log/ggaming-backup.log 2>&1
# Opcional: si configurás rclone con un remoto llamado "drive" (Google Drive, 15 GB gratis),
# también se sube una copia fuera del servidor.
# ============================================================
set -euo pipefail
cd "$(dirname "$0")/.."

DESTINO=backups
FECHA=$(date +%F_%H%M)
mkdir -p "$DESTINO"

# .backup de SQLite es seguro aunque la app esté escribiendo (copia consistente).
sqlite3 datos/ggaming.db ".backup '$DESTINO/ggaming-$FECHA.db'"
tar -czf "$DESTINO/fotos-$FECHA.tar.gz" -C datos uploads

# Borrar backups de más de 14 días.
find "$DESTINO" -type f -mtime +14 -delete

if command -v rclone >/dev/null && rclone listremotes | grep -q '^drive:'; then
  rclone copy "$DESTINO" drive:ggaming-backups --max-age 25h
  echo "$(date) backup subido a Google Drive"
fi
echo "$(date) backup OK: $DESTINO/ggaming-$FECHA.db"
