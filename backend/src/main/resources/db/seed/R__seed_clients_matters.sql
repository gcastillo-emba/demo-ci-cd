-- Seed idempotente del perfil demo (add-clients-matters, design D10): 25 clientes y 30 asuntos
-- 100 % sintéticos (nombres ficticios, identificaciones tributarias con prefijo DEMO-).
-- Fechas relativas a now(); los códigos usan el año en curso en America/La_Paz.
-- Idempotente: ids fijos y ON CONFLICT DO NOTHING; el correlativo nunca retrocede (GREATEST).

INSERT INTO clients (id, name, tax_id, created_at)
SELECT ('00000000-0000-4000-a000-' || lpad(c.n::text, 12, '0'))::uuid,
       c.name,
       c.tax_id,
       now() - make_interval(days => c.n)
FROM (VALUES
    (1,  'Agropecuaria Ficticia del Sur S.A.',     'DEMO-0001'),
    (2,  'Banco Imaginario de Fomento S.A.',       'DEMO-0002'),
    (3,  'Constructora Inventada Altiplano S.R.L.', 'DEMO-0003'),
    (4,  'Distribuidora Ejemplo Oriente S.A.',     NULL),
    (5,  'Energías Simuladas del Valle S.A.',      'DEMO-0005'),
    (6,  'Farmacéutica Supuesta Ltda.',            'DEMO-0006'),
    (7,  'Grupo Ficticio Horizonte S.A.',          NULL),
    (8,  'Hotelera Demo Salar S.R.L.',             'DEMO-0008'),
    (9,  'Importadora Prueba Pacífico S.A.',       'DEMO-0009'),
    (10, 'Joyería Ilusoria Potosí Ltda.',          NULL),
    (11, 'Klinker Demo Cementos S.A.',             'DEMO-0011'),
    (12, 'Laboratorios Ficticios Yungas S.A.',     'DEMO-0012'),
    (13, 'Minera Imaginaria Cordillera S.A.',      'DEMO-0013'),
    (14, 'Naviera Ejemplo Titicaca S.R.L.',        NULL),
    (15, 'Óptica Simulada Central Ltda.',          'DEMO-0015'),
    (16, 'Petrolera Supuesta Chaco S.A.',          'DEMO-0016'),
    (17, 'Química Demo Integral S.A.',             'DEMO-0017'),
    (18, 'Rentas Ficticias Urbanas S.R.L.',        NULL),
    (19, 'Seguros Imaginarios Unidos S.A.',        'DEMO-0019'),
    (20, 'Textil Ejemplo Amazonía Ltda.',          'DEMO-0020'),
    (21, 'Universidad Privada de Prueba',          'DEMO-0021'),
    (22, 'Viñedos Simulados Tarija S.A.',          'DEMO-0022'),
    (23, 'Water Demo Servicios S.R.L.',            NULL),
    (24, 'Xilografía Ficticia Editores Ltda.',     'DEMO-0024'),
    (25, 'Zafra Supuesta Agroindustrial S.A.',     'DEMO-0025')
) AS c(n, name, tax_id)
ON CONFLICT DO NOTHING;

INSERT INTO matters (id, code, client_id, title, description, status, created_at, closed_at)
SELECT ('00000000-0000-4000-b000-' || lpad(s.n::text, 12, '0'))::uuid,
       'AS-' || y.year || '-' || lpad(s.n::text, 4, '0'),
       ('00000000-0000-4000-a000-' || lpad(((s.n - 1) % 25 + 1)::text, 12, '0'))::uuid,
       (ARRAY[
           'Constitución de sociedad',
           'Revisión de contrato de suministro',
           'Litigio laboral de prueba',
           'Registro de marca ficticia',
           'Auditoría de cumplimiento',
           'Reestructuración de deuda simulada'
       ])[(s.n - 1) % 6 + 1] || ' (demo ' || s.n || ')',
       CASE WHEN s.n % 3 = 0 THEN 'Descripción sintética de un asunto de demostración.' END,
       CASE WHEN s.n % 4 = 0 THEN 'CLOSED' ELSE 'OPEN' END,
       now() - make_interval(hours => 40 - s.n),
       CASE WHEN s.n % 4 = 0 THEN now() - make_interval(hours => 39 - s.n) END
FROM generate_series(1, 30) AS s(n)
CROSS JOIN (SELECT extract(year FROM now() AT TIME ZONE 'America/La_Paz')::int AS year) AS y
ON CONFLICT DO NOTHING;

-- Alinea el correlativo anual con los códigos existentes para que las altas nuevas no colisionen.
INSERT INTO matter_code_seq (year, last_value)
SELECT substring(code FROM 4 FOR 4)::int, max(substring(code FROM 9 FOR 4)::int)
FROM matters
GROUP BY substring(code FROM 4 FOR 4)
ON CONFLICT (year) DO UPDATE SET last_value = GREATEST(matter_code_seq.last_value, EXCLUDED.last_value);
