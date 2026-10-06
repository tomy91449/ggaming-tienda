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

-- Datos internos de la app (ej: si ya se cargaron los datos de ejemplo).
CREATE TABLE IF NOT EXISTS meta (
    clave TEXT PRIMARY KEY,
    valor TEXT
);
