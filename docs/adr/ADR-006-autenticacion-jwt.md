# ADR-006 — Autenticación con JWT HS256

- **Estado:** Aceptado
- **Fecha:** 2026-11-07
- **Decisores:** humano + arquitecto
- **Relacionado con:** ADR-001, ADR-005

## Contexto

El POC necesita autenticar a usuarios con roles PARTNER (Socio) y LAWYER (Abogado) y autorizar por rol y por propiedad. Es una demo con datos sintéticos, un solo backend y un SPA servido en el mismo origen (nginx hace proxy de `/api`). No hay proveedor de identidad corporativo disponible ni justificado para el alcance.

## Decisión

- **Token:** JWT firmado **HS256** con Nimbus JOSE + JWT (vía `spring-security-oauth2-resource-server`). Claims: `sub` (userId), `role`, `iat`, `exp`. Sin PII adicional.
- **Validación del token:** el decoder fija `HS256` (descarta `alg: none` y la confusión RS/HS) y usa los validadores por defecto de Spring Security (`JwtValidators.createDefaultWithValidators`: marcas de tiempo con el `Clock` inyectable) más un `JwtClaimValidator` que exige **`exp`**. `JwtTimestampValidator` solo valida `exp` si existe, así que sin ese validador un token firmado sin `exp` no caducaría nunca. ITs de regresión: sin `exp`, `alg: none` y firmado con otra clave → 401.
- **TTL:** 2 horas. **Sin refresh token**: al expirar, el SPA redirige al login (401 → Problem Details).
- **Almacenamiento en el cliente:** `sessionStorage` (se borra al cerrar la pestaña). Se envía como `Authorization: Bearer`.
- **Usuarios:** sembrados por migración (perfil `demo`), sintéticos, con contraseñas hasheadas con **BCrypt**. Las credenciales de demo se documentan en el README.
- **Contraseñas de más de 72 bytes:** BCrypt solo considera 72 bytes y algunas versiones de Spring Security lanzan `IllegalArgumentException` por encima de ese límite. `BCryptPasswordHasher` captura esa excepción y devuelve `false`: el login responde `401 invalid-credentials`, nunca `500` (test unitario del adaptador e IT con 100 caracteres).
- **Secreto:** variable de entorno `APP_JWT_SECRET` (mínimo 256 bits). El secreto **falso** de demo se define en **un único lugar**: el valor por defecto del perfil `demo` en `backend/src/main/resources/application-demo.yml`. Lo usan `docker compose` (que pasa `APP_JWT_SECRET` al contenedor solo si está definido en el entorno, sin valor propio), `npm run dev` y los IT. Fuera del perfil `demo` no hay valor por defecto y el backend **falla al arrancar** sin secreto. Nunca se commitea en `.env`.
- **Endpoints:** `POST /api/auth/login` (público), `GET /api/me`; el resto requiere token. La autorización fina vive en `application.service` (ADR-001), cubierta por JaCoCo.
- **Logs:** nunca se registra el token, la contraseña ni el hash; el MDC solo lleva `userId` y `requestId`.

### CSRF

La protección CSRF de Spring Security se **ignora solo para `/api/**`** (`csrf.ignoringRequestMatchers("/api/**")`) y queda **activa en cualquier otra ruta**. Motivo: la API es stateless y la credencial viaja en la cabecera `Authorization: Bearer`, que el navegador nunca adjunta por su cuenta (no hay cookies de sesión ni autenticación básica). Un sitio malicioso puede provocar una petición cruzada, pero sin el token no llega autenticada, así que CSRF no aplica. Desactivar CSRF de forma global era demasiado amplio (alerta CodeQL `java/spring-disabled-csrf-protection`, bitácora #19). Si algún día la sesión pasa a una cookie (ver alternativas), hay que reactivar CSRF también en `/api/**`.

### Rate limiting de login en nginx

`frontend/nginx.conf` limita `POST /api/auth/login` por IP de cliente (ASVS 2.2.1): `limit_req_zone $binary_remote_addr zone=login:10m rate=5r/m` y `limit_req zone=login burst=5 nodelay`. El exceso recibe **429** en `application/problem+json` (`code` = `too-many-requests`, `title`/`detail` en español, `traceId` vacío porque la petición no llega al backend) desde una `location` con nombre vía `error_page 429`, con `Retry-After: 60`. El contrato documenta el 429 en `login` y el SPA muestra el `detail` (con un respaldo en español si el cuerpo no llega como problem+json). Se hace en nginx, no en el backend, porque es el único punto de entrada publicado (el backend solo escucha en `127.0.0.1` en compose) y no agrega estado ni dependencias al backend stateless.

### Anti-enumeración de usuarios

El login responde lo mismo para email inexistente, contraseña errónea o usuario inactivo (`401 invalid-credentials`). Además, si el email no existe, `AuthenticationService` ejecuta igualmente una verificación BCrypt contra un hash ficticio (`PasswordHasher.dummyCheck`, hash precalculado al construir el adaptador, mismo costo que los reales). Así el tiempo de respuesta no revela qué cuentas existen. Un test unitario verifica que el hasher se invoca con un email desconocido.

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
- **Negativas:** un token robado vale hasta 2 h (no hay revocación); `sessionStorage` es legible por JS ante un XSS; HS256 comparte el secreto entre emisión y verificación. El rate limiting es por IP: usuarios detrás de una misma NAT comparten el cupo, y si se agrega otro proxy delante de nginx hay que tomar la IP real (`real_ip_header`). Los E2E también pasan por nginx desde una sola IP: una suite con más de 6 logins por minuto recibe 429, así que los E2E de C1–C4 deben reutilizar la sesión (por ejemplo `storageState` de Playwright) en lugar de iniciar sesión en cada test.
- **Gates que la verifican:** ITs de seguridad (401/403, token expirado con `Clock`, token sin `exp`, `alg: none`, firma con otra clave, contraseña de 100 caracteres), test unitario anti-enumeración, Vitest del 429 en el login, arranque de nginx con la configuración real en el E2E (compose `--wait`), test de captura de logs sin PII, gitleaks y push protection para el secreto, revisión de seguridad.

## Supuestos explícitos

1. El frontend no renderiza HTML no confiable (React escapa por defecto; no se usa `dangerouslySetInnerHTML`).
2. Las sesiones de uso de la demo duran menos de 2 h.
3. No hay requisito de cierre de sesión forzado ni de revocación inmediata.

## Cómo revertir

Cambiar el adaptador `adapters.out.security` por sesión con cookie `HttpOnly` o por un resource server OIDC. Gracias a los puertos de ADR-001 el dominio y la aplicación no cambian; sí cambian el contrato (`/auth/login`), el cliente del SPA y los E2E de login.
