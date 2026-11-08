-- Seed idempotente del perfil demo: usuarios 100 % sintéticos.
-- Contraseña de demo (documentada en README y .env.example): Demo2026!  — solo para la demo.
-- Hash BCrypt (costo 10) precalculado; BCryptPasswordHasherTest verifica que coincide.
INSERT INTO users (id, email, full_name, password_hash, role, active) VALUES
    ('00000000-0000-4000-8000-000000000001', 'socia@asuntos.test',    'Socia Demo',   '$2a$10$Nu4Qg98z9R7eQNeWWIcbr.qPITSjr1zXHFpGovbR1w5yZ1zGpkn2G', 'PARTNER', TRUE),
    ('00000000-0000-4000-8000-000000000002', 'abogado1@asuntos.test', 'Abogado Uno',  '$2a$10$Nu4Qg98z9R7eQNeWWIcbr.qPITSjr1zXHFpGovbR1w5yZ1zGpkn2G', 'LAWYER',  TRUE),
    ('00000000-0000-4000-8000-000000000003', 'abogado2@asuntos.test', 'Abogada Dos',  '$2a$10$Nu4Qg98z9R7eQNeWWIcbr.qPITSjr1zXHFpGovbR1w5yZ1zGpkn2G', 'LAWYER',  TRUE),
    ('00000000-0000-4000-8000-000000000004', 'abogado3@asuntos.test', 'Abogado Tres', '$2a$10$Nu4Qg98z9R7eQNeWWIcbr.qPITSjr1zXHFpGovbR1w5yZ1zGpkn2G', 'LAWYER',  TRUE)
ON CONFLICT (email) DO NOTHING;
