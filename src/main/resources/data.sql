-- Datos de ejemplo: se cargan UNA sola vez (la primera vez que arranca la app).
-- Así, si borrás un producto de ejemplo desde el admin, no vuelve a aparecer al reiniciar.
INSERT OR IGNORE INTO producto (id, nombre, descripcion, categoria, precio, vendido)
SELECT * FROM (VALUES
 (1,  'NVIDIA RTX 3060 12GB',        'MSI Ventus 2X, poco uso, con caja',   'PLACA_VIDEO', 380000, 0),
 (2,  'AMD Radeon RX 6600 8GB',      'Sapphire Pulse, impecable',           'PLACA_VIDEO', 290000, 0),
 (3,  'NVIDIA GTX 1660 Super 6GB',   'Gigabyte OC, sin caja',               'PLACA_VIDEO', 180000, 1),
 (4,  'AMD Ryzen 5 5600',            'Con cooler stock Wraith Stealth',     'PROCESADOR',  160000, 0),
 (5,  'Intel Core i5-12400F',        'Bandeja, sin cooler',                 'PROCESADOR',  150000, 0),
 (6,  'Kingston Fury 16GB DDR4',     '2x8GB 3200MHz CL16',                  'RAM',          55000, 0),
 (7,  'Corsair Vengeance 32GB DDR5', '2x16GB 5600MHz',                      'RAM',         120000, 0),
 (8,  'SSD Kingston NV2 1TB',        'NVMe M.2 PCIe 4.0, 95% de salud',     'DISCO',        70000, 0),
 (9,  'HDD Seagate Barracuda 2TB',   '7200 RPM SATA',                       'DISCO',        45000, 1),
 (10, 'Fuente Corsair CV650',        '650W 80 Plus Bronze',                 'FUENTE',       75000, 0),
 (11, 'Gigabyte B550M DS3H',         'AM4, micro ATX, BIOS actualizado',    'MOTHERBOARD', 110000, 0),
 (12, 'Gabinete Sentey con 3 fans',  'Vidrio templado, ATX',                'GABINETE',     60000, 0)
)
WHERE NOT EXISTS (SELECT 1 FROM meta WHERE clave = 'datos_ejemplo');

INSERT OR IGNORE INTO meta (clave, valor) VALUES ('datos_ejemplo', 'cargados');

-- Historial de ventas de ejemplo (también una sola vez), para probar la memoria de precios.
INSERT INTO venta (producto_id, nombre, categoria, precio_publicado, precio_venta, fecha)
SELECT * FROM (VALUES
 (3,    'NVIDIA GTX 1660 Super 6GB',         'PLACA_VIDEO', 180000, 170000, '2026-09-20'),
 (9,    'HDD Seagate Barracuda 2TB',         'DISCO',        45000,  45000, '2026-09-02'),
 (NULL, 'Gigabyte RTX 3060 Eagle 12GB',      'PLACA_VIDEO', 370000, 350000, '2026-07-14'),
 (NULL, 'ASUS RTX 3060 Ti Dual 8GB',         'PLACA_VIDEO', 430000, 410000, '2026-08-03'),
 (NULL, 'AMD Ryzen 5 5600X',                 'PROCESADOR',  175000, 165000, '2026-06-21'),
 (NULL, 'Kingston Fury Beast 8GB DDR4 3200', 'RAM',          30000,  27000, '2026-08-28'),
 (NULL, 'SSD Kingston NV2 500GB',            'DISCO',        42000,  40000, '2026-05-10'),
 (NULL, 'Fuente Corsair CV550',              'FUENTE',       62000,  58000, '2026-04-18')
)
WHERE NOT EXISTS (SELECT 1 FROM meta WHERE clave = 'ventas_ejemplo');

INSERT OR IGNORE INTO meta (clave, valor) VALUES ('ventas_ejemplo', 'cargadas');
