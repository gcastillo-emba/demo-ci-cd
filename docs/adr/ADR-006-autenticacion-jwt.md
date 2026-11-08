# ADR-006 — Autenticación con JWT HS256

- **Estado:** Aceptado
- **Fecha:** 2026-11-07
- **Decisores:** humano + arquitecto
- **Relacionado con:** ADR-001, ADR-005

## Contexto

El POC necesita autenticar a usuarios con roles PARTNER (Socio) y LAWYER (Abogado) y autorizar por rol y por propiedad. Es una demo con datos sintéticos, un solo backend y un SPA servido en el mismo origen (nginx hace proxy de `/api`). No hay proveedor de identidad corporativo disponible ni justificado para el alcance.

## Decisión

- **Token:** JWT firmado **HS256** con Nimbus JOSE + JWT (vía `spring-security-oauth2-resource-server`). Claims: `sub` (userId), `role`, `iat`, `exp`. Sin PII adicional.
- **TTL:** 2 horas. **Sin refresh token**: al expirar, el SPA redirige al login (401 → Problem Details).
- **Almacenamiento en el cliente:** `sessionStorage` (se borra al cerrar la pestaña). Se envía como `Authorization: Bearer`.
- **Usuarios:** sembrados por migración (perfil `demo`), sintéticos, con contraseñas hasheadas con **BCrypt**. Las credenciales de demo se documentan en el README.
- **Secreto:** variable de entorno `APP_JWT_SECRET` (mínimo 256 bits). El único valor por defecto existe en `docker-compose.yml` para desarrollo local; el backend **falla al arrancar** si no hay secreto fuera de ese contexto. Nunca se commitea en `.env`.
- **Endpoints:** `POST /api/auth/login` (público), `GET /api/me`; el resto requiere token. La autorización fina vive en `application.service` (ADR-001), cubierta por JaCoCo.
- **Logs:** nunca se registra el token, la contraseña ni el hash; el MDC solo lleva `userId` y `requestId`.

## Alternativas consideradas

| Alternativa                         | Por qué no                                                                                                       |
| ----------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| Sesión con cookie `HttpOnly` + CSRF | Más segura frente a XSS, pero agrega manejo de CSRF y estado de sesión; el POC prioriza simplicidad (revisable). |
| RS256 / JWKS                        | Útil con varios emisores o verificadores; aquí hay uno solo.                                                     |
| OIDC con IdP (Keycloak, Entra ID)   | Infraestructura extra y configuración fuera del alcance del POC.                                                 |
| `localStorage`                      | Persiste entre pestañas y reinicios; mayor ventana de exposición ante XSS.                                       |
| Refresh tokens                      | Complejidad (rotación, revocación) sin beneficio para sesiones de demo cortas.                                   |

## Consecuencias

- **Positivas:** stateless, simple de probar en ITs y E2E; sin dependencias externas.
- **Negativas:** un token robado vale hasta 2 h (no hay revocación); `sessionStorage` es legible por JS ante un XSS; HS256 comparte el secreto entre emisión y verificación.
- **Gates que la verifican:** ITs de seguridad (401/403, token expirado con `Clock`), test de captura de logs sin PII, gitleaks y push protection para el secreto, revisión de seguridad.

## Supuestos explícitos

1. El frontend no renderiza HTML no confiable (React escapa por defecto; no se usa `dangerouslySetInnerHTML`).
2. Las sesiones de uso de la demo duran menos de 2 h.
3. No hay requisito de cierre de sesión forzado ni de revocación inmediata.

## Cómo revertir

Cambiar el adaptador `adapters.out.security` por sesión con cookie `HttpOnly` o por un resource server OIDC. Gracias a los puertos de ADR-001 el dominio y la aplicación no cambian; sí cambian el contrato (`/auth/login`), el cliente del SPA y los E2E de login.
