-- Seed idempotente del perfil demo (add-time-entries, design D9): registros de horas 100 %
-- sintéticos de los abogados de demo sobre asuntos de demo. Fechas relativas a hoy en
-- America/La_Paz (N >= 1 días atrás, nunca futuras); ningún abogado supera 24 h en un día.
-- Idempotente: ids fijos y ON CONFLICT DO NOTHING. Se llama R__seed_time_entries (y no
-- R__demo_time_entries) para que Flyway lo aplique después de los seeds de usuarios y asuntos
-- (los repetibles se ordenan por descripción).

INSERT INTO time_entries (id, matter_id, lawyer_id, work_date, hours_tenths, description, status,
                          rejection_reason, created_at)
SELECT ('00000000-0000-4000-c000-' || lpad(e.n::text, 12, '0'))::uuid,
       ('00000000-0000-4000-b000-' || lpad(e.matter_n::text, 12, '0'))::uuid,
       ('00000000-0000-4000-8000-' || lpad(e.lawyer_n::text, 12, '0'))::uuid,
       t.today - e.days_ago,
       e.tenths,
       e.description,
       e.status,
       e.reason,
       now() - make_interval(days => e.days_ago, hours => 2)
FROM (VALUES
    (1,  2, 1,  1, 15, 'PENDING',  NULL, 'Revisión de borrador de estatutos (demo).'),
    (2,  2, 2,  1, 30, 'PENDING',  NULL, 'Reunión con el cliente ficticio sobre el contrato (demo).'),
    (3,  2, 3,  2, 45, 'APPROVED', NULL, 'Preparación de memorial de prueba (demo).'),
    (4,  2, 5,  3, 20, 'REJECTED', 'Descripción insuficiente', 'Trabajo varios (demo).'),
    (5,  2, 6,  5, 60, 'APPROVED', NULL, 'Investigación de jurisprudencia sintética (demo).'),
    (6,  2, 1,  9, 10, 'PENDING',  NULL, 'Llamada de seguimiento fuera de ventana (demo).'),
    (7,  3, 2,  1, 25, 'PENDING',  NULL, 'Redacción de cláusulas de suministro (demo).'),
    (8,  3, 7,  2, 80, 'PENDING',  NULL, 'Due diligence documental de prueba (demo).'),
    (9,  3, 9,  4, 35, 'APPROVED', NULL, 'Registro de marca ficticia ante la oficina (demo).'),
    (10, 3, 4,  6, 15, 'PENDING',  NULL, 'Cierre de expediente en asunto ya cerrado (demo).'),
    (11, 4, 10, 1, 40, 'PENDING',  NULL, 'Auditoría de cumplimiento simulada (demo).'),
    (12, 4, 11, 3, 50, 'REJECTED', 'Asunto incorrecto', 'Análisis de deuda simulada (demo).'),
    (13, 4, 13, 7, 70, 'APPROVED', NULL, 'Audiencia de conciliación de prueba (demo).')
) AS e(n, lawyer_n, matter_n, days_ago, tenths, status, reason, description)
CROSS JOIN (SELECT (now() AT TIME ZONE 'America/La_Paz')::date AS today) AS t
WHERE EXISTS (SELECT 1 FROM matters m
              WHERE m.id = ('00000000-0000-4000-b000-' || lpad(e.matter_n::text, 12, '0'))::uuid)
  AND EXISTS (SELECT 1 FROM users u
              WHERE u.id = ('00000000-0000-4000-8000-' || lpad(e.lawyer_n::text, 12, '0'))::uuid)
ON CONFLICT DO NOTHING;
