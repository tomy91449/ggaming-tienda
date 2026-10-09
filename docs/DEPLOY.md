# Publicar GGaming en producción (gratis)

Resultado: la tienda en `https://ggaming.duckdns.org` (o el nombre que elijas), con HTTPS, backups
diarios y el servidor protegido. Costo: $0.

- **Servidor:** Oracle Cloud "Always Free" (máquina virtual gratis para siempre, con disco propio).
- **Dominio:** DuckDNS (subdominio gratis e instantáneo).
- **HTTPS:** Caddy, con certificado de Let's Encrypt que se renueva solo.

---

## 1. Crear el servidor en Oracle Cloud (20 min)

1. Registrate en **cloud.oracle.com** → "Start for free". Pide tarjeta **solo para verificar
   identidad** (no cobra mientras uses recursos "Always Free"). Elegí como región la más cercana
   (por ejemplo, Brazil East - São Paulo). **La región no se puede cambiar después.**
2. Menú → **Compute → Instances → Create instance**:
   - **Image:** Canonical **Ubuntu 24.04**.
   - **Shape:** Ampere → **VM.Standard.A1.Flex** con **2 OCPU y 12 GB** de RAM (dentro de lo gratis).
     Si dice "Out of capacity", reintentá más tarde o probá otro "availability domain".
   - **SSH keys:** "Generate a key pair for me" y **descargá la clave privada** (archivo `.key`).
     Guardala bien: sin ella no podés entrar.
3. Cuando la instancia esté "Running", anotá su **Public IP address**.
4. **Abrir los puertos 80 y 443:** en la instancia → **Subnet** → **Default Security List** →
   **Add Ingress Rules**:
   - Source CIDR `0.0.0.0/0`, IP Protocol TCP, Destination Port Range `80,443`.

> Oracle puede reclamar instancias gratis que pasen mucho tiempo casi sin uso. Si te pasa, con
> los backups (paso 7) la recreás en minutos.

## 2. Dominio gratis con DuckDNS (5 min)

1. Entrá a **duckdns.org** y logueate (con GitHub o Google).
2. Escribí el nombre (por ejemplo, `ggaming`) → **add domain**.
3. En **current ip** poné la IP pública del servidor → **update ip**.
4. Tu dominio queda: `ggaming.duckdns.org`.

## 3. Entrar al servidor (desde tu PC)

```powershell
# PowerShell, en la carpeta donde guardaste la clave:
ssh -i .\ssh-key-oracle.key ubuntu@IP_DEL_SERVIDOR
```

Si Windows se queja de los permisos de la clave: botón derecho → Propiedades → Seguridad → dejar
solo a tu usuario con permiso de lectura.

## 4. Preparar el servidor (una sola vez)

```bash
git clone https://github.com/tomy91449/ggaming-tienda.git
cd ggaming-tienda
bash scripts/preparar-servidor.sh
exit        # salir y volver a entrar para que Docker funcione sin sudo
```

El script instala Docker, activa las actualizaciones de seguridad automáticas, instala fail2ban
(bloquea a quien intente adivinar el acceso por SSH), deja el SSH solo con clave y abre en el
firewall únicamente los puertos 80 y 443.

## 5. Configurar los secretos

```bash
cd ggaming-tienda
cp .env.example .env
nano .env           # completar y guardar con Ctrl+O, Enter, Ctrl+X
chmod 600 .env      # solo tu usuario puede leerlo
```

Valores obligatorios en producción:

| Variable | Qué poner |
|---|---|
| `DOMINIO` | `ggaming.duckdns.org` (sin `https://`) |
| `ADMIN_USER` | Un usuario que no sea `admin` |
| `ADMIN_PASSWORD` | Mínimo 12 caracteres (mejor 20+). La app **no arranca** con una débil |
| `SECRET_KEY` | Texto aleatorio de 32+ caracteres. Generalo con: `openssl rand -hex 32` |
| `WHATSAPP_NUMBER` | El número donde vas a **leer** los pedidos (sin el bot conectado, tu WhatsApp) |
| `GEMINI_API_KEY` | Opcional: tu clave de Google AI Studio |

No hace falta poner `BASE_URL` ni `COOKIE_SECURE`: el `docker-compose.yml` los arma a partir de
`DOMINIO`.

## 6. Arrancar la tienda

```bash
mkdir -p datos/uploads && sudo chown -R 10001:10001 datos   # carpeta de la base y las fotos
docker compose up -d --build                                   # la primera vez tarda unos minutos
docker compose logs -f app                                     # ver que arranque (Ctrl+C para salir)
```

Entrá a `https://ggaming.duckdns.org`. La primera visita puede tardar unos segundos mientras Caddy
obtiene el certificado HTTPS.

## 7. Backups automáticos

```bash
sudo crontab -e
# agregar esta línea (backup todos los días a las 4 AM):
0 4 * * * bash /home/ubuntu/ggaming-tienda/scripts/backup.sh >> /var/log/ggaming-backup.log 2>&1
```

Guarda la base y las fotos en `backups/` durante 14 días. **Recomendado:** copia fuera del servidor
con Google Drive (15 GB gratis):

```bash
sudo apt-get install -y rclone
rclone config        # "n" (nuevo) → nombre: drive → tipo: drive → seguir los pasos
```

Restaurar un backup: `docker compose stop app`, copiar el `.db` a `datos/ggaming.db`, descomprimir
las fotos en `datos/uploads` y `docker compose start app`.

## 8. Monitoreo (gratis)

En **uptimerobot.com** creá un monitor HTTPS a tu dominio: te avisa por mail si la tienda se cae.

## Actualizar la tienda después de un cambio

```bash
cd ggaming-tienda
git pull
docker compose up -d --build
```

## Checklist de seguridad

- [ ] `ADMIN_PASSWORD` larga y distinta a cualquier otra contraseña tuya.
- [ ] `SECRET_KEY` generada con `openssl rand -hex 32`.
- [ ] `.env` con permisos `600` y **nunca** subido a GitHub.
- [ ] En Oracle, abiertos solo los puertos 22, 80 y 443.
- [ ] Clave de Gemini restringida a "Generative Language API" en console.cloud.google.com.
- [ ] Backup diario funcionando (revisar `backups/` al día siguiente).
- [ ] Monitor de UptimeRobot creado.
