// Generado desde api/openapi.yaml — no editar. Ejecuta npm run api:generate.
export interface paths {
    "/api/auth/login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Iniciar sesión
         * @description Valida email y contraseña y devuelve un access token JWT con vigencia de 2 horas.
         */
        post: operations["login"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Usuario autenticado
         * @description Devuelve el usuario dueño del token.
         */
        get: operations["getMe"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/features": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Feature flags
         * @description Feature flags visibles para la interfaz.
         */
        get: operations["getFeatures"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        /**
         * @description Rol del usuario. `PARTNER` = Socio, `LAWYER` = Abogado.
         * @enum {string}
         */
        Role: "PARTNER" | "LAWYER";
        User: {
            /** Format: uuid */
            id: string;
            /** Format: email */
            email: string;
            fullName: string;
            role: components["schemas"]["Role"];
        };
        LoginRequest: {
            /** Format: email */
            email: string;
            /** Format: password */
            password: string;
        };
        LoginResponse: {
            /** @description JWT HS256. */
            accessToken: string;
            /** @constant */
            tokenType: "Bearer";
            /**
             * Format: int32
             * @description Segundos de vigencia del token (7200 = 2 h).
             * @example 7200
             */
            expiresIn: number;
            user: components["schemas"]["User"];
        };
        Features: {
            /** @description Asistente de descripción (C4) habilitado. */
            descriptionAssistant: boolean;
        };
        /** @description Detalle de error RFC 9457. */
        Problem: {
            /**
             * Format: uri
             * @description `https://asuntos-horas.example/problems/{code}`.
             */
            type: string;
            /** @description Resumen legible en español. */
            title: string;
            /** Format: int32 */
            status: number;
            /** @description Explicación en español de esta ocurrencia. */
            detail: string;
            /** Format: uri-reference */
            instance?: string;
            /** @description Código estable en inglés (kebab-case). */
            code: string;
            /** @description Identificador de traza para correlacionar con los logs. */
            traceId: string;
            errors?: components["schemas"]["FieldError"][];
        };
        FieldError: {
            field: string;
            /** @description Mensaje en español. */
            message: string;
        };
        /**
         * @description Sobre genérico de paginación. Las capacidades lo especializan con
         *     `allOf: [{ $ref: Page }, { properties: { items: { items: { $ref: X } } } }]`.
         */
        Page: {
            items: unknown[];
            /**
             * Format: int32
             * @description Página (base 0).
             */
            page: number;
            /** Format: int32 */
            size: number;
            /** Format: int64 */
            totalItems: number;
            /** Format: int32 */
            totalPages: number;
        };
    };
    responses: {
        /** @description Falta el token o no es válido (`code` = `unauthenticated`). */
        Unauthorized: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                /**
                 * @example {
                 *       "type": "https://asuntos-horas.example/problems/unauthenticated",
                 *       "title": "No autenticado",
                 *       "status": 401,
                 *       "detail": "Debes iniciar sesión para acceder a este recurso.",
                 *       "code": "unauthenticated",
                 *       "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
                 *     }
                 */
                "application/problem+json": components["schemas"]["Problem"];
            };
        };
        /** @description La solicitud no cumple las validaciones (`code` = `validation-error`). */
        ValidationError: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                /**
                 * @example {
                 *       "type": "https://asuntos-horas.example/problems/validation-error",
                 *       "title": "Solicitud inválida",
                 *       "status": 400,
                 *       "detail": "La solicitud tiene campos inválidos.",
                 *       "code": "validation-error",
                 *       "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
                 *       "errors": [
                 *         {
                 *           "field": "email",
                 *           "message": "Debe ser un email válido."
                 *         }
                 *       ]
                 *     }
                 */
                "application/problem+json": components["schemas"]["Problem"];
            };
        };
        /** @description Error inesperado del servidor (`code` = `internal-error`). */
        InternalError: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/problem+json": components["schemas"]["Problem"];
            };
        };
    };
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    login: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                /**
                 * @example {
                 *       "email": "socia@asuntos.test",
                 *       "password": "Demo2026!"
                 *     }
                 */
                "application/json": components["schemas"]["LoginRequest"];
            };
        };
        responses: {
            /** @description Credenciales válidas. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LoginResponse"];
                };
            };
            400: components["responses"]["ValidationError"];
            /** @description Credenciales inválidas (`code` = `invalid-credentials`). */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    /**
                     * @example {
                     *       "type": "https://asuntos-horas.example/problems/invalid-credentials",
                     *       "title": "Credenciales inválidas",
                     *       "status": 401,
                     *       "detail": "El email o la contraseña no son correctos.",
                     *       "code": "invalid-credentials",
                     *       "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
                     *     }
                     */
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            /**
             * @description Demasiados intentos de inicio de sesión desde la misma IP (`code` = `too-many-requests`).
             *     Lo emite el proxy nginx (5 por minuto por IP, ráfaga de 5; ADR-006), no el backend, por
             *     eso `traceId` llega vacío. Incluye la cabecera `Retry-After` en segundos.
             */
            429: {
                headers: {
                    /** @description Segundos que conviene esperar antes de reintentar. */
                    "Retry-After"?: number;
                    [name: string]: unknown;
                };
                content: {
                    /**
                     * @example {
                     *       "type": "https://asuntos-horas.example/problems/too-many-requests",
                     *       "title": "Demasiados intentos",
                     *       "status": 429,
                     *       "detail": "Demasiados intentos de inicio de sesión. Espera un minuto e inténtalo de nuevo.",
                     *       "code": "too-many-requests",
                     *       "traceId": ""
                     *     }
                     */
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            500: components["responses"]["InternalError"];
        };
    };
    getMe: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Usuario autenticado. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["User"];
                };
            };
            401: components["responses"]["Unauthorized"];
            500: components["responses"]["InternalError"];
        };
    };
    getFeatures: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Estado de los feature flags. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Features"];
                };
            };
            401: components["responses"]["Unauthorized"];
            500: components["responses"]["InternalError"];
        };
    };
}
