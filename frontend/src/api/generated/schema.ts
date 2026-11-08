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
    "/api/clients": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar clientes
         * @description Lista paginada de clientes para cualquier usuario autenticado (`PARTNER` o `LAWYER`),
         *     ordenada por `name` ascendente (y `id` para desempatar). `q` filtra por coincidencia
         *     parcial sobre `name` sin distinguir mayúsculas ni minúsculas. Una página más allá de la
         *     última responde `200` con `items` vacío.
         */
        get: operations["listClients"];
        put?: never;
        /**
         * Crear cliente
         * @description Crea un cliente. Solo `PARTNER`. `name` y `taxId` se recortan (espacios al inicio y al
         *     final) antes de validar y guardar; `taxId` es único (comparación exacta).
         */
        post: operations["createClient"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/clients/{clientId}": {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del cliente. */
                clientId: components["parameters"]["ClientIdPath"];
            };
            cookie?: never;
        };
        /**
         * Obtener cliente
         * @description Devuelve un cliente a cualquier usuario autenticado.
         */
        get: operations["getClient"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/matters": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar asuntos
         * @description Lista paginada de asuntos para cualquier usuario autenticado, ordenada por `code`
         *     descendente (más recientes primero). Filtros opcionales y combinables: `clientId`,
         *     `status` y `q` (coincidencia parcial sin distinguir mayúsculas ni minúsculas sobre
         *     `title` o `code`). Una página más allá de la última responde `200` con `items` vacío.
         */
        get: operations["listMatters"];
        put?: never;
        /**
         * Crear asunto
         * @description Crea un asunto en estado `OPEN` para un cliente existente. Solo `PARTNER`. El servidor
         *     genera `code` (`AS-YYYY-NNNN`, año en `America/La_Paz`); `code`, `status`, `closedAt` e
         *     `id` enviados en el cuerpo se ignoran. `title` se recorta antes de validar y guardar.
         *     Un `clientId` inexistente responde `404 not-found`.
         */
        post: operations["createMatter"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/matters/{matterId}": {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del asunto. */
                matterId: components["parameters"]["MatterIdPath"];
            };
            cookie?: never;
        };
        /**
         * Obtener asunto
         * @description Devuelve un asunto a cualquier usuario autenticado.
         */
        get: operations["getMatter"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/matters/{matterId}/close": {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del asunto. */
                matterId: components["parameters"]["MatterIdPath"];
            };
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Cerrar asunto
         * @description Cierra un asunto `OPEN` (irreversible): queda `CLOSED` con `closedAt` igual al instante
         *     del cierre. Solo `PARTNER`. Sin cuerpo. El cierre no depende de los registros de horas
         *     del asunto.
         */
        post: operations["closeMatter"];
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
        Client: {
            /** Format: uuid */
            id: string;
            name: string;
            /** @description Identificación tributaria (texto libre); `null` si no se informó. */
            taxId: string | null;
            /** Format: date-time */
            createdAt: string;
        };
        /**
         * @description Alta de cliente. `name` y `taxId` se recortan antes de validar; un valor que queda vacío
         *     tras recortar responde `400 validation-error`.
         */
        ClientCreate: {
            /** @example Inversiones Andinas S.A. */
            name: string;
            /**
             * @description Opcional. Único entre clientes (comparación exacta).
             * @example 0000000001
             */
            taxId?: string;
        };
        /**
         * @description Estado del asunto. `OPEN` = Abierto, `CLOSED` = Cerrado.
         * @enum {string}
         */
        MatterStatus: "OPEN" | "CLOSED";
        Matter: {
            /** Format: uuid */
            id: string;
            /**
             * @description Código generado por el servidor (`AS-YYYY-NNNN`).
             * @example AS-2026-0012
             */
            code: string;
            /** Format: uuid */
            clientId: string;
            clientName: string;
            title: string;
            description: string | null;
            status: components["schemas"]["MatterStatus"];
            /** Format: date-time */
            createdAt: string;
            /**
             * Format: date-time
             * @description Instante del cierre; `null` mientras el asunto está `OPEN`.
             */
            closedAt: string | null;
        };
        /**
         * @description Alta de asunto. `title` se recorta antes de validar. Los campos de solo lectura
         *     (`id`, `code`, `status`, `closedAt`) no forman parte del esquema y se ignoran si se envían.
         */
        MatterCreate: {
            /** Format: uuid */
            clientId: string;
            /** @example Due diligence adquisición planta */
            title: string;
            description?: string;
        };
        /** @description Página de clientes. */
        ClientPage: components["schemas"]["Page"] & {
            items: components["schemas"]["Client"][];
        };
        /** @description Página de asuntos. */
        MatterPage: components["schemas"]["Page"] & {
            items: components["schemas"]["Matter"][];
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
        /** @description El rol del usuario no permite la operación (`code` = `forbidden`). */
        Forbidden: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                /**
                 * @example {
                 *       "type": "https://asuntos-horas.example/problems/forbidden",
                 *       "title": "Acción no permitida para su rol",
                 *       "status": 403,
                 *       "detail": "Tu rol no permite realizar esta acción.",
                 *       "code": "forbidden",
                 *       "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
                 *     }
                 */
                "application/problem+json": components["schemas"]["Problem"];
            };
        };
        /** @description El recurso no existe (`code` = `not-found`). */
        NotFound: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                /**
                 * @example {
                 *       "type": "https://asuntos-horas.example/problems/not-found",
                 *       "title": "Recurso no encontrado",
                 *       "status": 404,
                 *       "detail": "El recurso solicitado no existe.",
                 *       "code": "not-found",
                 *       "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
                 *     }
                 */
                "application/problem+json": components["schemas"]["Problem"];
            };
        };
        /**
         * @description Error inesperado del servidor (`code` = `internal-error`). El `detail` es genérico y la
         *     respuesta no incluye trazas, nombres de clases, SQL ni mensajes internos.
         */
        InternalError: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                /**
                 * @example {
                 *       "type": "https://asuntos-horas.example/problems/internal-error",
                 *       "title": "Error interno",
                 *       "status": 500,
                 *       "detail": "Ocurrió un error inesperado. Si persiste, informe el identificador de traza.",
                 *       "code": "internal-error",
                 *       "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
                 *     }
                 */
                "application/problem+json": components["schemas"]["Problem"];
            };
        };
    };
    parameters: {
        /** @description Texto de búsqueda (coincidencia parcial, sin distinguir mayúsculas ni minúsculas). */
        Q: string;
        /** @description Página (base 0). Negativa o no numérica responde `400 validation-error`. */
        PageParam: number;
        /** @description Tamaño de página (1 a 100). Fuera de rango o no numérico responde `400 validation-error`. */
        SizeParam: number;
        /** @description Id del cliente. */
        ClientIdPath: string;
        /** @description Id del asunto. */
        MatterIdPath: string;
    };
    requestBodies: never;
    headers: {
        /** @description Ruta del recurso creado (`/api/clients/{clientId}` o `/api/matters/{matterId}`). */
        Location: string;
    };
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
    listClients: {
        parameters: {
            query?: {
                /** @description Texto de búsqueda (coincidencia parcial, sin distinguir mayúsculas ni minúsculas). */
                q?: components["parameters"]["Q"];
                /** @description Página (base 0). Negativa o no numérica responde `400 validation-error`. */
                page?: components["parameters"]["PageParam"];
                /** @description Tamaño de página (1 a 100). Fuera de rango o no numérico responde `400 validation-error`. */
                size?: components["parameters"]["SizeParam"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Página de clientes. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ClientPage"];
                };
            };
            400: components["responses"]["ValidationError"];
            401: components["responses"]["Unauthorized"];
            500: components["responses"]["InternalError"];
        };
    };
    createClient: {
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
                 *       "name": "Inversiones Andinas S.A.",
                 *       "taxId": "0000000001"
                 *     }
                 */
                "application/json": components["schemas"]["ClientCreate"];
            };
        };
        responses: {
            /** @description Cliente creado. */
            201: {
                headers: {
                    Location: components["headers"]["Location"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Client"];
                };
            };
            400: components["responses"]["ValidationError"];
            401: components["responses"]["Unauthorized"];
            403: components["responses"]["Forbidden"];
            /** @description El `taxId` ya está registrado (`code` = `client-tax-id-taken`). */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    /**
                     * @example {
                     *       "type": "https://asuntos-horas.example/problems/client-tax-id-taken",
                     *       "title": "Identificación tributaria ya registrada",
                     *       "status": 409,
                     *       "detail": "Ya existe un cliente con esa identificación tributaria.",
                     *       "code": "client-tax-id-taken",
                     *       "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
                     *     }
                     */
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            500: components["responses"]["InternalError"];
        };
    };
    getClient: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del cliente. */
                clientId: components["parameters"]["ClientIdPath"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Cliente encontrado. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Client"];
                };
            };
            400: components["responses"]["ValidationError"];
            401: components["responses"]["Unauthorized"];
            404: components["responses"]["NotFound"];
            500: components["responses"]["InternalError"];
        };
    };
    listMatters: {
        parameters: {
            query?: {
                /** @description Texto de búsqueda (coincidencia parcial, sin distinguir mayúsculas ni minúsculas). */
                q?: components["parameters"]["Q"];
                /** @description Filtra por cliente. */
                clientId?: string;
                /** @description Filtra por estado (`OPEN` o `CLOSED`); otro valor responde `400`. */
                status?: components["schemas"]["MatterStatus"];
                /** @description Página (base 0). Negativa o no numérica responde `400 validation-error`. */
                page?: components["parameters"]["PageParam"];
                /** @description Tamaño de página (1 a 100). Fuera de rango o no numérico responde `400 validation-error`. */
                size?: components["parameters"]["SizeParam"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Página de asuntos. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MatterPage"];
                };
            };
            400: components["responses"]["ValidationError"];
            401: components["responses"]["Unauthorized"];
            500: components["responses"]["InternalError"];
        };
    };
    createMatter: {
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
                 *       "clientId": "7b0f4b8e-2c1a-4d4b-9a51-3f0f6c2d9e10",
                 *       "title": "Due diligence adquisición planta"
                 *     }
                 */
                "application/json": components["schemas"]["MatterCreate"];
            };
        };
        responses: {
            /** @description Asunto creado. */
            201: {
                headers: {
                    Location: components["headers"]["Location"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Matter"];
                };
            };
            400: components["responses"]["ValidationError"];
            401: components["responses"]["Unauthorized"];
            403: components["responses"]["Forbidden"];
            404: components["responses"]["NotFound"];
            500: components["responses"]["InternalError"];
        };
    };
    getMatter: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del asunto. */
                matterId: components["parameters"]["MatterIdPath"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Asunto encontrado. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Matter"];
                };
            };
            400: components["responses"]["ValidationError"];
            401: components["responses"]["Unauthorized"];
            404: components["responses"]["NotFound"];
            500: components["responses"]["InternalError"];
        };
    };
    closeMatter: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del asunto. */
                matterId: components["parameters"]["MatterIdPath"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Asunto cerrado. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Matter"];
                };
            };
            400: components["responses"]["ValidationError"];
            401: components["responses"]["Unauthorized"];
            403: components["responses"]["Forbidden"];
            404: components["responses"]["NotFound"];
            /** @description El asunto ya está cerrado (`code` = `matter-already-closed`). */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    /**
                     * @example {
                     *       "type": "https://asuntos-horas.example/problems/matter-already-closed",
                     *       "title": "El asunto ya está cerrado",
                     *       "status": 409,
                     *       "detail": "El asunto ya estaba cerrado; no se modificó.",
                     *       "code": "matter-already-closed",
                     *       "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
                     *     }
                     */
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            500: components["responses"]["InternalError"];
        };
    };
}
