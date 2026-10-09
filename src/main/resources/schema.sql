-- Se ejecuta en cada arranque; IF NOT EXISTS evita borrar datos existentes.
CREATE TABLE IF NOT EXISTS producto (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre      TEXT    NOT NULL,
    descripcion TEXT,
    categoria   TEXT    NOT NULL,           -- nombre del enum Categoria
    precio      INTEGER NOT NULL,           -- pesos, sin centavos
    vendido     INTEGER NOT NULL DEFAULT 0, -- 0 = disponible, 1 = vendido
    foto        TEXT                        -- ruta de la foto principal (la que se ve en el catálogo)
);

-- Todas las fotos de cada producto (puede tener varias).
CREATE TABLE IF NOT EXISTS foto (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    producto_id INTEGER NOT NULL,
    ruta        TEXT    NOT NULL,           -- ej: /fotos/3f2a...jpg
    orden       INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_foto_producto ON foto (producto_id);

-- HISTORIAL DE VENTAS (memoria de precios).
-- Guarda una copia del nombre y la categoría: si después borrás el producto,
-- la venta queda registrada igual y sigue sirviendo para sugerir precios.
CREATE TABLE IF NOT EXISTS venta (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    producto_id      INTEGER,                -- puede quedar apuntando a un producto borrado
    nombre           TEXT    NOT NULL,
    categoria        TEXT    NOT NULL,
    precio_publicado INTEGER NOT NULL,
    precio_venta     INTEGER NOT NULL,
    fecha            TEXT    NOT NULL        -- formato ISO: 2026-05-10
);
CREATE INDEX IF NOT EXISTS idx_venta_fecha ON venta (fecha);

-- BOT DE WHATSAPP: conversaciones (memoria del bot por cliente).
CREATE TABLE IF NOT EXISTS chat_mensaje (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    telefono TEXT    NOT NULL,   -- número del cliente, ej: 5493511234567
    rol      TEXT    NOT NULL,   -- CLIENTE | BOT | AVISO (lo que se le avisó al dueño)
    texto    TEXT    NOT NULL,
    fecha    TEXT    NOT NULL    -- ISO: 2026-10-09T18:30:00Z
);
CREATE INDEX IF NOT EXISTS idx_chat_telefono ON chat_mensaje (telefono, id);

-- Estado de cada chat: BOT (responde solo) o HUMANO (lo atiende el dueño, el bot calla).
CREATE TABLE IF NOT EXISTS chat_estado (
    telefono      TEXT PRIMARY KEY,
    modo          TEXT NOT NULL,
    pausado_hasta TEXT            -- hasta cuándo dura el modo HUMANO
);

-- Turnos agendados por el bot (visitas para probar y retirar).
CREATE TABLE IF NOT EXISTS turno (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    telefono  TEXT NOT NULL,
    dia       TEXT NOT NULL,     -- 2026-10-10
    hora      TEXT NOT NULL,     -- 18:30
    productos TEXT,
    creado    TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_turno_dia ON turno (dia, hora);

-- Datos internos de la app (ej: si ya se cargaron los datos de ejemplo).
CREATE TABLE IF NOT EXISTS meta (
    clave TEXT PRIMARY KEY,
    valor TEXT
);
