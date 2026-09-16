-- ---------------------------------------------------------------------------
-- Datos de ejemplo. Solo se ejecuta con el perfil `postgres`
-- (spring.sql.init.mode=always + spring.jpa.defer-datasource-initialization=true).
--
-- Son los mismos tres blueprints que carga InMemoryBlueprintPersistence, asi la
-- API se comporta igual con o sin base de datos.
-- El script es idempotente: reiniciar la app sobre el mismo volumen no duplica filas.
-- point_order conserva el orden de los puntos (importa para los filtros).
-- Los id no se fijan a mano (columna IDENTITY) para no desincronizar la secuencia.
-- ---------------------------------------------------------------------------

INSERT INTO blueprints (author, name)
SELECT 'john', 'house'
WHERE NOT EXISTS (SELECT 1 FROM blueprints WHERE author = 'john' AND name = 'house');

INSERT INTO blueprints (author, name)
SELECT 'john', 'garage'
WHERE NOT EXISTS (SELECT 1 FROM blueprints WHERE author = 'john' AND name = 'garage');

INSERT INTO blueprints (author, name)
SELECT 'jane', 'garden'
WHERE NOT EXISTS (SELECT 1 FROM blueprints WHERE author = 'jane' AND name = 'garden');

INSERT INTO blueprint_points (blueprint_id, point_order, x, y)
SELECT b.id, v.ord, v.x, v.y
FROM blueprints b
JOIN (VALUES (0, 0, 0), (1, 10, 0), (2, 10, 10), (3, 0, 10)) AS v(ord, x, y) ON TRUE
WHERE b.author = 'john' AND b.name = 'house'
  AND NOT EXISTS (SELECT 1 FROM blueprint_points p WHERE p.blueprint_id = b.id);

INSERT INTO blueprint_points (blueprint_id, point_order, x, y)
SELECT b.id, v.ord, v.x, v.y
FROM blueprints b
JOIN (VALUES (0, 5, 5), (1, 15, 5), (2, 15, 15)) AS v(ord, x, y) ON TRUE
WHERE b.author = 'john' AND b.name = 'garage'
  AND NOT EXISTS (SELECT 1 FROM blueprint_points p WHERE p.blueprint_id = b.id);

INSERT INTO blueprint_points (blueprint_id, point_order, x, y)
SELECT b.id, v.ord, v.x, v.y
FROM blueprints b
JOIN (VALUES (0, 2, 2), (1, 3, 4), (2, 6, 7)) AS v(ord, x, y) ON TRUE
WHERE b.author = 'jane' AND b.name = 'garden'
  AND NOT EXISTS (SELECT 1 FROM blueprint_points p WHERE p.blueprint_id = b.id);
