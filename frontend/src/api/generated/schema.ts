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
    "/api/time-entries": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Listar registros de horas
         * @description Lista paginada de registros de horas para cualquier usuario autenticado. Un `PARTNER` ve
         *     los registros de todos los abogados que cumplan los filtros; un `LAWYER` ve solo los suyos
         *     y su `lawyerId` se ignora (sin error). Filtros opcionales y combinables: `matterId`,
         *     `lawyerId`, `status` y el rango inclusivo `from`..`to` sobre `workDate`. Orden con `sort`
         *     (enum cerrado): `workDate,desc` por defecto (a igual fecha, `createdAt` descendente) o
         *     `createdAt,asc` (cola de aprobaciones). Un `status` o `sort` no admitido, o `from`
         *     posterior a `to`, responde `400 validation-error`. Una página más allá de la última
         *     responde `200` con `items` vacío.
         */
        get: operations["listTimeEntries"];
        put?: never;
        /**
         * Registrar horas
         * @description Registra horas del abogado autenticado en un asunto `OPEN`. Solo `LAWYER` (un `PARTNER`
         *     recibe `403 forbidden`). El registro nace `PENDING`, con `lawyerId` del usuario
         *     autenticado y `createdAt` del servidor; `id`, `lawyerId`, `lawyerName`, `matterCode`,
         *     `status`, `rejectionReason`, `createdAt`, `editableUntil` y `version` enviados en el
         *     cuerpo se ignoran. `description` se recorta antes de validar. Un `matterId` inexistente
         *     responde `404 not-found`. Las reglas de negocio responden `422` (ver la respuesta).
         */
        post: operations["createTimeEntry"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/time-entries/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del registro de horas. Un valor que no es UUID responde `400 validation-error`. */
                id: components["parameters"]["TimeEntryIdPath"];
            };
            cookie?: never;
        };
        get?: never;
        /**
         * Editar registro de horas
         * @description Edita `matterId`, `workDate`, `hours` y `description` de un registro propio enviando la
         *     `version` vigente. Solo `LAWYER` (un `PARTNER` recibe `403 forbidden`) y solo el autor
         *     (`403 not-entry-owner`). Solo registros `PENDING` (`409 entry-not-pending`) y dentro de la
         *     ventana de edición (`422 edit-window-expired`: hoy en `America/La_Paz` ≤ `editableUntil`).
         *     Una `version` obsoleta responde `409 concurrent-modification` sin aplicar cambios. Aplica
         *     las mismas reglas que el alta; `matter-closed` se evalúa sobre el asunto destino y, para
         *     el límite diario, las horas previas del propio registro no cuentan. Autor, estado, motivo
         *     de rechazo y `createdAt` nunca cambian; los campos de solo lectura enviados se ignoran.
         *     Responde `200` con el registro y una `version` nueva.
         */
        put: operations["updateTimeEntry"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/time-entries/{id}/approve": {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del registro de horas. Un valor que no es UUID responde `400 validation-error`. */
                id: components["parameters"]["TimeEntryIdPath"];
            };
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Aprobar registro de horas
         * @description Aprueba un registro `PENDING` de cualquier abogado; queda `APPROVED` (terminal). Solo
         *     `PARTNER` (un `LAWYER` recibe `403 forbidden`). Sin cuerpo y sin `version`. No evalúa
         *     `matter-closed`: los pendientes de un asunto cerrado pueden aprobarse.
         */
        post: operations["approveTimeEntry"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/time-entries/{id}/reject": {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del registro de horas. Un valor que no es UUID responde `400 validation-error`. */
                id: components["parameters"]["TimeEntryIdPath"];
            };
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Rechazar registro de horas
         * @description Rechaza un registro `PENDING` con un motivo; queda `REJECTED` (terminal) con su
         *     `rejectionReason`. Solo `PARTNER` (un `LAWYER` recibe `403 forbidden`). Sin `version`.
         *     `reason` se recorta y se valida en el servicio: ausente, en blanco o con más de 500
         *     caracteres responde `422 rejection-reason-required` (no `400`). `409 entry-not-pending` se
         *     evalúa antes que el motivo. No evalúa `matter-closed`.
         */
        post: operations["rejectTimeEntry"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/dashboard/summary": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Resumen del tablero de horas
         * @description Resumen agregado de horas `APPROVED` y `PENDING` (los registros `REJECTED` no cuentan) por
         *     asunto y por abogado, más los totales del período, para cualquier usuario autenticado
         *     (no hay `403`). Un `PARTNER` ve los registros de todos los abogados; un `LAWYER` solo los
         *     suyos y `byLawyer` contiene siempre exactamente su propia fila (con ceros si no tiene horas
         *     en la ventana). La ventana filtra por `workDate` (inclusiva en ambos extremos):
         *
         *     - Sin `from` ni `to`: últimos 30 días, `[hoy-29, hoy]`, con "hoy" en `America/La_Paz`.
         *     - Con `from` y `to`: `[from, to]`, como máximo 92 días contando ambos extremos.
         *
         *     La respuesta devuelve la ventana efectiva en `from` y `to`. Solo aparecen filas con horas
         *     contadas (salvo la fila propia del `LAWYER`); `byMatter` se ordena por `matterCode` y
         *     `byLawyer` por `lawyerName`, ambos ascendentes. Un período sin horas responde `200` con
         *     listas vacías y totales en cero (nunca `404`). No se pagina. Los parámetros de consulta
         *     desconocidos (p. ej. `lawyerId`) se ignoran.
         *
         *     Responden `400 validation-error` (sin datos agregados), con `errors[].field`: un solo
         *     parámetro de la ventana (el ausente), `from` posterior a `to` (`from`), un rango de más de
         *     92 días (`to`) y una fecha que no es ISO `YYYY-MM-DD` válida o no existe (el parámetro
         *     afectado).
         */
        get: operations["getDashboardSummary"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/assistant/description-suggestions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Sugerir descripción de un registro de horas
         * @description Devuelve una descripción profesional en español (como máximo 300 caracteres, sin saltos
         *     de línea, correos ni teléfonos) sugerida a partir de una nota libre del abogado. No crea
         *     ni modifica registros: la sugerencia no se persiste. `provider` identifica al adaptador
         *     que la generó (`fake` por defecto, determinista; `anthropic` opcional).
         *
         *     Precedencia de verificaciones (sin invocar al proveedor salvo en el último paso):
         *
         *     1. Sin token o token inválido → `401 unauthenticated`.
         *     2. Indicador `descriptionAssistant` apagado → `404 feature-disabled` para cualquier rol
         *        (incluido `PARTNER`): el endpoint es indistinguible de uno inexistente.
         *     3. Rol distinto de `LAWYER` → `403 forbidden` (aunque el cuerpo sea inválido).
         *     4. Validación → `400 validation-error`: `note` se recorta antes de validar; ausente, vacía
         *        o solo con espacios agrega `errors[]` con `field: note` y `code: assistant-note-empty`;
         *        más de 500 caracteres (code points, tras el recorte) agrega `field: note` y
         *        `code: assistant-note-too-long`; `matterId` que no es UUID agrega `field: matterId`.
         *     5. `matterId` de un asunto inexistente → `404 not-found` (cualquier estado del asunto es
         *        válido; solo se usa su código como contexto, nunca datos del cliente).
         *     6. Proveedor que no responde en el tiempo límite (8 s por defecto), falla o devuelve un
         *        texto vacío → `503 assistant-unavailable`.
         */
        post: operations["suggestDescription"];
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
            /**
             * @description Asistente de descripción (C4) habilitado (`app.features.description-assistant`):
             *     `false` por defecto en producción y `true` en el perfil `demo`.
             */
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
            /**
             * @description Opcional. Código estable en inglés (kebab-case) del error de este campo, cuando la
             *     operación lo define (p. ej. `assistant-note-empty`, `assistant-note-too-long` en
             *     `suggestDescription`). Su ausencia no cambia el significado de `field` y `message`.
             */
            code?: string;
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
        /**
         * @description Estado del registro de horas. `PENDING` = Pendiente, `APPROVED` = Aprobada,
         *     `REJECTED` = Rechazada. `APPROVED` y `REJECTED` son terminales.
         * @enum {string}
         */
        TimeEntryStatus: "PENDING" | "APPROVED" | "REJECTED";
        /**
         * @description Orden del listado de registros (enum cerrado). `workDate,desc` = `workDate` descendente y,
         *     a igual fecha, `createdAt` descendente (por defecto). `createdAt,asc` = `createdAt`
         *     ascendente (cola de aprobaciones).
         * @default workDate,desc
         * @enum {string}
         */
        TimeEntrySort: "workDate,desc" | "createdAt,asc";
        TimeEntry: {
            /** Format: uuid */
            id: string;
            /** Format: uuid */
            matterId: string;
            /**
             * @description Código del asunto (`AS-YYYY-NNNN`).
             * @example AS-2026-0012
             */
            matterCode: string;
            /**
             * Format: uuid
             * @description Abogado autor del registro (siempre el usuario autenticado que lo creó).
             */
            lawyerId: string;
            /** @description Nombre completo del abogado autor. */
            lawyerName: string;
            /**
             * Format: date
             * @description Fecha de trabajo (no posterior a hoy en `America/La_Paz`).
             */
            workDate: string;
            /**
             * @description Horas en incrementos de 0,1 (se almacenan como décimas enteras 1..240).
             * @example 1.5
             */
            hours: number;
            description: string;
            status: components["schemas"]["TimeEntryStatus"];
            /** @description Motivo de rechazo; `null` salvo cuando `status` = `REJECTED`. */
            rejectionReason: string | null;
            /**
             * Format: date-time
             * @description Instante de creación fijado por el servidor.
             */
            createdAt: string;
            /**
             * Format: date
             * @description Última fecha (en `America/La_Paz`) en que el autor puede editar el registro: fecha de
             *     creación en La Paz + 7 días. Se informa siempre, aunque el registro ya no sea
             *     `PENDING`; el servidor sigue siendo la autoridad.
             */
            editableUntil: string;
            /**
             * Format: int64
             * @description Versión para concurrencia optimista; se envía en `TimeEntryUpdate`.
             */
            version: number;
        };
        /**
         * @description Alta de registro de horas. `description` se recorta antes de validar; vacía tras recortar
         *     o con más de 2000 caracteres responde `400 validation-error`. `hours` es un número decimal
         *     (p. ej. `1.5`) sin restricciones de formato en el contrato: el incremento de 0,1 y el
         *     rango `0 < hours ≤ 24` son reglas de negocio que responden `422`
         *     (`hours-invalid-increment`, `hours-out-of-range`). Los campos controlados por el servidor
         *     (`id`, `lawyerId`, `lawyerName`, `matterCode`, `status`, `rejectionReason`, `createdAt`,
         *     `editableUntil`, `version`) no forman parte del esquema y se ignoran si se envían.
         */
        TimeEntryCreate: {
            /** Format: uuid */
            matterId: string;
            /**
             * Format: date
             * @example 2026-11-06
             */
            workDate: string;
            /** @example 1.5 */
            hours: number;
            /** @example Revisión de contrato de compraventa y notas. */
            description: string;
        };
        /**
         * @description Edición de registro de horas: mismas reglas que `TimeEntryCreate` más `version`
         *     obligatoria (ausente → `400 validation-error` con `errors[].field` = `version`; obsoleta →
         *     `409 concurrent-modification`). Autor, estado, motivo de rechazo y `createdAt` no forman
         *     parte del esquema y se ignoran si se envían.
         */
        TimeEntryUpdate: {
            /** Format: uuid */
            matterId: string;
            /**
             * Format: date
             * @example 2026-11-06
             */
            workDate: string;
            /** @example 3.5 */
            hours: number;
            description: string;
            /**
             * Format: int64
             * @description Versión vigente del registro (la devuelta por el servidor).
             */
            version: number;
        };
        /**
         * @description Rechazo de un registro. `reason` se recorta y se valida en el servicio (no en el
         *     contrato): ausente, en blanco o con más de 500 caracteres responde
         *     `422 rejection-reason-required`. Un cuerpo ausente o que no es JSON responde
         *     `400 validation-error`.
         */
        RejectRequest: {
            /**
             * @description Motivo del rechazo (1 a 500 caracteres tras recortar).
             * @example Descripción insuficiente
             */
            reason?: string;
        };
        /** @description Página de registros de horas. */
        TimeEntryPage: components["schemas"]["Page"] & {
            items: components["schemas"]["TimeEntry"][];
        };
        /** @description Problem Details de `daily-hours-exceeded` (RFC 9457 con miembros de extensión). */
        DailyHoursExceededProblem: components["schemas"]["Problem"] & {
            /**
             * @description Límite diario de horas por abogado (24).
             * @example 24
             */
            limitHours: number;
            /**
             * @description Horas `PENDING` + `APPROVED` del abogado ese día sin el registro nuevo (o sin las
             *     horas previas del registro editado).
             * @example 20
             */
            alreadyLoggedHours: number;
        };
        /**
         * @description Problem Details de una regla de negocio de registros de horas (`422`): un `Problem` y, si
         *     `code` = `daily-hours-exceeded`, además `limitHours` y `alreadyLoggedHours`.
         */
        TimeEntryRuleProblem: components["schemas"]["DailyHoursExceededProblem"] | components["schemas"]["Problem"];
        /**
         * @description Totales del período dentro del alcance del usuario. Cada valor es igual a la suma de la
         *     columna correspondiente en `byMatter` y en `byLawyer`.
         */
        HoursTotals: {
            /**
             * @description Suma de horas `APPROVED` (decimal con una décima).
             * @example 9.5
             */
            approvedHours: number;
            /**
             * @description Suma de horas `PENDING` (decimal con una décima).
             * @example 4.5
             */
            pendingHours: number;
        };
        /** @description Horas contadas de un asunto en la ventana. */
        MatterHoursRow: {
            /** Format: uuid */
            matterId: string;
            /**
             * @description Código del asunto (`AS-YYYY-NNNN`).
             * @example AS-2026-0001
             */
            matterCode: string;
            /**
             * @description Título del asunto.
             * @example Constitución de sociedad comercial
             */
            matterTitle: string;
            /**
             * @description Suma de horas `APPROVED` del asunto (decimal con una décima).
             * @example 3.5
             */
            approvedHours: number;
            /**
             * @description Suma de horas `PENDING` del asunto (decimal con una décima).
             * @example 0.5
             */
            pendingHours: number;
        };
        /**
         * @description Horas contadas de un abogado en la ventana. Para un `LAWYER` es siempre su propia fila,
         *     con ceros si no tiene horas contadas.
         */
        LawyerHoursRow: {
            /** Format: uuid */
            lawyerId: string;
            /**
             * @description Nombre completo del abogado.
             * @example Ana Pérez
             */
            lawyerName: string;
            /**
             * @description Suma de horas `APPROVED` del abogado (decimal con una décima).
             * @example 6
             */
            approvedHours: number;
            /**
             * @description Suma de horas `PENDING` del abogado (decimal con una décima).
             * @example 2
             */
            pendingHours: number;
        };
        /**
         * @description Resumen del tablero para la ventana efectiva `[from, to]` (inclusiva). `byMatter` ordenado
         *     por `matterCode` ascendente y `byLawyer` por `lawyerName` ascendente; solo filas con horas
         *     contadas, salvo la fila propia del `LAWYER`.
         */
        DashboardSummary: {
            /**
             * Format: date
             * @description Primer día de la ventana efectiva (inclusivo).
             * @example 2026-10-09
             */
            from: string;
            /**
             * Format: date
             * @description Último día de la ventana efectiva (inclusivo).
             * @example 2026-11-07
             */
            to: string;
            totals: components["schemas"]["HoursTotals"];
            /** @description Una fila por asunto con horas contadas; vacía si no hay horas. */
            byMatter: components["schemas"]["MatterHoursRow"][];
            /**
             * @description Una fila por abogado con horas contadas (`PARTNER`; vacía si no hay horas) o exactamente
             *     la fila propia (`LAWYER`).
             */
            byLawyer: components["schemas"]["LawyerHoursRow"][];
        };
        /**
         * @description Nota libre del abogado para sugerir una descripción. `note` es obligatoria en la práctica,
         *     pero el esquema no la marca como requerida ni fija `minLength`/`maxLength`: la ausencia,
         *     el vacío (tras recortar) y el exceso de 500 caracteres los resuelve el guardrail del
         *     servidor con `400 validation-error` y `errors[].code` propio (`assistant-note-empty`,
         *     `assistant-note-too-long`). Las propiedades desconocidas se ignoran.
         */
        DescriptionSuggestionRequest: {
            /**
             * @description Nota libre (se recorta antes de validar; de 1 a 500 caracteres Unicode tras el
             *     recorte). Se trata solo como datos, nunca como instrucciones; los correos y teléfonos
             *     se redactan antes de llegar al proveedor.
             * @example reunion cliente revisar contrato arrendamiento
             */
            note?: string;
            /**
             * Format: uuid
             * @description Opcional. Asunto existente (cualquier estado) usado solo como contexto (su código).
             *     Formato inválido responde `400 validation-error`; inexistente, `404 not-found`.
             */
            matterId?: string;
        };
        DescriptionSuggestionResponse: {
            /**
             * @description Descripción sugerida en español: no vacía, como máximo 300 caracteres Unicode, sin
             *     saltos de línea y sin correos ni teléfonos (reemplazados por `[correo]` /
             *     `[teléfono]`). Texto plano: la interfaz no debe interpretarlo como HTML ni Markdown.
             * @example Reunión con el cliente para revisar el contrato de arrendamiento.
             */
            suggestion: string;
            provider: components["schemas"]["AssistantProvider"];
        };
        /**
         * @description Adaptador que generó la sugerencia (`fake` determinista por defecto).
         * @enum {string}
         */
        AssistantProvider: "fake" | "anthropic";
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
         * @description El registro no está `PENDING` (`code` = `entry-not-pending`): `APPROVED` y `REJECTED` son
         *     terminales. No se aplica ningún cambio.
         */
        EntryNotPending: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                /**
                 * @example {
                 *       "type": "https://asuntos-horas.example/problems/entry-not-pending",
                 *       "title": "El registro ya no está pendiente",
                 *       "status": 409,
                 *       "detail": "El registro ya fue aprobado o rechazado; no se modificó.",
                 *       "code": "entry-not-pending",
                 *       "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
                 *     }
                 */
                "application/problem+json": components["schemas"]["Problem"];
            };
        };
        /**
         * @description El registro incumple una regla de negocio. `code` es uno de (en orden de evaluación):
         *     `edit-window-expired` (solo edición), `matter-closed`, `future-date` (posterior a hoy en
         *     `America/La_Paz`), `hours-invalid-increment` (no múltiplo de 0,1),
         *     `hours-out-of-range` (fuera de `0 < hours ≤ 24`) y `daily-hours-exceeded` (la suma
         *     `PENDING` + `APPROVED` del abogado ese día superaría 24). Solo `daily-hours-exceeded`
         *     agrega `limitHours` y `alreadyLoggedHours` (esquema `DailyHoursExceededProblem`).
         */
        TimeEntryRuleViolation: {
            headers: {
                [name: string]: unknown;
            };
            content: {
                "application/problem+json": components["schemas"]["TimeEntryRuleProblem"];
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
        /**
         * @description Texto de búsqueda (coincidencia parcial, sin distinguir mayúsculas ni minúsculas). Máximo
         *     200 caracteres; más largo responde `400 validation-error`.
         */
        Q: string;
        /**
         * @description Página (base 0, máximo 10000). Negativa, mayor que 10000 o no numérica responde
         *     `400 validation-error`.
         */
        PageParam: number;
        /** @description Tamaño de página (1 a 100). Fuera de rango o no numérico responde `400 validation-error`. */
        SizeParam: number;
        /** @description Id del cliente. */
        ClientIdPath: string;
        /** @description Id del asunto. */
        MatterIdPath: string;
        /** @description Id del registro de horas. Un valor que no es UUID responde `400 validation-error`. */
        TimeEntryIdPath: string;
    };
    requestBodies: never;
    headers: {
        /**
         * @description Ruta del recurso creado (`/api/clients/{clientId}`, `/api/matters/{matterId}` o
         *     `/api/time-entries/{id}`).
         */
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
    listClients: {
        parameters: {
            query?: {
                /**
                 * @description Texto de búsqueda (coincidencia parcial, sin distinguir mayúsculas ni minúsculas). Máximo
                 *     200 caracteres; más largo responde `400 validation-error`.
                 */
                q?: components["parameters"]["Q"];
                /**
                 * @description Página (base 0, máximo 10000). Negativa, mayor que 10000 o no numérica responde
                 *     `400 validation-error`.
                 */
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
                /**
                 * @description Texto de búsqueda (coincidencia parcial, sin distinguir mayúsculas ni minúsculas). Máximo
                 *     200 caracteres; más largo responde `400 validation-error`.
                 */
                q?: components["parameters"]["Q"];
                /** @description Filtra por cliente. */
                clientId?: string;
                /** @description Filtra por estado (`OPEN` o `CLOSED`); otro valor responde `400`. */
                status?: components["schemas"]["MatterStatus"];
                /**
                 * @description Página (base 0, máximo 10000). Negativa, mayor que 10000 o no numérica responde
                 *     `400 validation-error`.
                 */
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
    listTimeEntries: {
        parameters: {
            query?: {
                /** @description Filtra por asunto. */
                matterId?: string;
                /** @description Filtra por abogado. Solo aplica a `PARTNER`; para `LAWYER` se ignora. */
                lawyerId?: string;
                /** @description Filtra por estado (`PENDING`, `APPROVED` o `REJECTED`); otro valor responde `400`. */
                status?: components["schemas"]["TimeEntryStatus"];
                /** @description Fecha de trabajo mínima (inclusiva, `YYYY-MM-DD`). Posterior a `to` responde `400`. */
                from?: string;
                /** @description Fecha de trabajo máxima (inclusiva, `YYYY-MM-DD`). */
                to?: string;
                /**
                 * @description Página (base 0, máximo 10000). Negativa, mayor que 10000 o no numérica responde
                 *     `400 validation-error`.
                 */
                page?: components["parameters"]["PageParam"];
                /** @description Tamaño de página (1 a 100). Fuera de rango o no numérico responde `400 validation-error`. */
                size?: components["parameters"]["SizeParam"];
                /**
                 * @description Orden del listado. `workDate,desc` (por defecto; desempate `createdAt` descendente) o
                 *     `createdAt,asc`. Otro valor responde `400 validation-error` con `errors[].field` = `sort`.
                 */
                sort?: components["schemas"]["TimeEntrySort"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Página de registros de horas. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TimeEntryPage"];
                };
            };
            400: components["responses"]["ValidationError"];
            401: components["responses"]["Unauthorized"];
            500: components["responses"]["InternalError"];
        };
    };
    createTimeEntry: {
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
                 *       "matterId": "3c9a1f2e-8b7d-4e6f-a1b2-c3d4e5f60718",
                 *       "workDate": "2026-11-06",
                 *       "hours": 1.5,
                 *       "description": "Revisión de contrato de compraventa y notas."
                 *     }
                 */
                "application/json": components["schemas"]["TimeEntryCreate"];
            };
        };
        responses: {
            /** @description Registro creado en estado `PENDING`. */
            201: {
                headers: {
                    Location: components["headers"]["Location"];
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TimeEntry"];
                };
            };
            400: components["responses"]["ValidationError"];
            401: components["responses"]["Unauthorized"];
            403: components["responses"]["Forbidden"];
            404: components["responses"]["NotFound"];
            422: components["responses"]["TimeEntryRuleViolation"];
            500: components["responses"]["InternalError"];
        };
    };
    updateTimeEntry: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del registro de horas. Un valor que no es UUID responde `400 validation-error`. */
                id: components["parameters"]["TimeEntryIdPath"];
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                /**
                 * @example {
                 *       "matterId": "3c9a1f2e-8b7d-4e6f-a1b2-c3d4e5f60718",
                 *       "workDate": "2026-11-06",
                 *       "hours": 3.5,
                 *       "description": "Revisión de contrato y reunión con el cliente.",
                 *       "version": 0
                 *     }
                 */
                "application/json": components["schemas"]["TimeEntryUpdate"];
            };
        };
        responses: {
            /** @description Registro actualizado. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TimeEntry"];
                };
            };
            400: components["responses"]["ValidationError"];
            401: components["responses"]["Unauthorized"];
            /**
             * @description El rol no permite editar (`code` = `forbidden`, p. ej. un `PARTNER`) o el registro es
             *     de otro abogado (`code` = `not-entry-owner`).
             */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            404: components["responses"]["NotFound"];
            /**
             * @description El registro no está `PENDING` (`code` = `entry-not-pending`) o la `version` enviada no
             *     es la vigente (`code` = `concurrent-modification`). No se aplica ningún cambio.
             */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            422: components["responses"]["TimeEntryRuleViolation"];
            500: components["responses"]["InternalError"];
        };
    };
    approveTimeEntry: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del registro de horas. Un valor que no es UUID responde `400 validation-error`. */
                id: components["parameters"]["TimeEntryIdPath"];
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Registro aprobado. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TimeEntry"];
                };
            };
            400: components["responses"]["ValidationError"];
            401: components["responses"]["Unauthorized"];
            403: components["responses"]["Forbidden"];
            404: components["responses"]["NotFound"];
            409: components["responses"]["EntryNotPending"];
            500: components["responses"]["InternalError"];
        };
    };
    rejectTimeEntry: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                /** @description Id del registro de horas. Un valor que no es UUID responde `400 validation-error`. */
                id: components["parameters"]["TimeEntryIdPath"];
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                /**
                 * @example {
                 *       "reason": "Descripción insuficiente"
                 *     }
                 */
                "application/json": components["schemas"]["RejectRequest"];
            };
        };
        responses: {
            /** @description Registro rechazado. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TimeEntry"];
                };
            };
            400: components["responses"]["ValidationError"];
            401: components["responses"]["Unauthorized"];
            403: components["responses"]["Forbidden"];
            404: components["responses"]["NotFound"];
            409: components["responses"]["EntryNotPending"];
            /** @description Falta el motivo, está en blanco o supera 500 caracteres (`code` = `rejection-reason-required`). */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    /**
                     * @example {
                     *       "type": "https://asuntos-horas.example/problems/rejection-reason-required",
                     *       "title": "Motivo de rechazo obligatorio",
                     *       "status": 422,
                     *       "detail": "Indica un motivo de rechazo de 1 a 500 caracteres.",
                     *       "code": "rejection-reason-required",
                     *       "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
                     *     }
                     */
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            500: components["responses"]["InternalError"];
        };
    };
    getDashboardSummary: {
        parameters: {
            query?: {
                /**
                 * @description Primer día de la ventana (inclusivo, `YYYY-MM-DD`). Debe enviarse junto con `to`; no
                 *     puede ser posterior a `to`.
                 * @example 2026-10-09
                 */
                from?: string;
                /**
                 * @description Último día de la ventana (inclusivo, `YYYY-MM-DD`). Debe enviarse junto con `from`; el
                 *     rango `from`..`to` admite como máximo 92 días contando ambos extremos.
                 * @example 2026-11-07
                 */
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Resumen agregado de la ventana efectiva. */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    /**
                     * @example {
                     *       "from": "2026-10-09",
                     *       "to": "2026-11-07",
                     *       "totals": {
                     *         "approvedHours": 9.5,
                     *         "pendingHours": 4.5
                     *       },
                     *       "byMatter": [
                     *         {
                     *           "matterId": "3c9a1f2e-8b7d-4e6f-a1b2-c3d4e5f60718",
                     *           "matterCode": "AS-2026-0001",
                     *           "matterTitle": "Constitución de sociedad comercial",
                     *           "approvedHours": 9.5,
                     *           "pendingHours": 0.5
                     *         },
                     *         {
                     *           "matterId": "7d2b4c6e-1a3f-4b5d-9e8f-0a1b2c3d4e5f",
                     *           "matterCode": "AS-2026-0002",
                     *           "matterTitle": "Revisión de contrato de arrendamiento",
                     *           "approvedHours": 0,
                     *           "pendingHours": 4
                     *         }
                     *       ],
                     *       "byLawyer": [
                     *         {
                     *           "lawyerId": "5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b",
                     *           "lawyerName": "Ana Pérez",
                     *           "approvedHours": 6,
                     *           "pendingHours": 2
                     *         },
                     *         {
                     *           "lawyerId": "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
                     *           "lawyerName": "Bruno Rojas",
                     *           "approvedHours": 3.5,
                     *           "pendingHours": 2.5
                     *         }
                     *       ]
                     *     }
                     */
                    "application/json": components["schemas"]["DashboardSummary"];
                };
            };
            /**
             * @description La ventana no es válida (`code` = `validation-error`): solo uno de `from`/`to`, `from`
             *     posterior a `to`, rango de más de 92 días o fecha con formato inválido o inexistente.
             *     `errors[]` indica el parámetro afectado. No incluye datos agregados.
             */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            401: components["responses"]["Unauthorized"];
            500: components["responses"]["InternalError"];
        };
    };
    suggestDescription: {
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
                 *       "note": "reunion cliente revisar contrato arrendamiento",
                 *       "matterId": "3c9a1f2e-8b7d-4e6f-a1b2-c3d4e5f60718"
                 *     }
                 */
                "application/json": components["schemas"]["DescriptionSuggestionRequest"];
            };
        };
        responses: {
            /** @description Sugerencia generada (no persistida). */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    /**
                     * @example {
                     *       "suggestion": "Reunión con el cliente para revisar el contrato de arrendamiento.",
                     *       "provider": "fake"
                     *     }
                     */
                    "application/json": components["schemas"]["DescriptionSuggestionResponse"];
                };
            };
            /**
             * @description La solicitud no cumple las validaciones (`code` = `validation-error`). Para `note`,
             *     `errors[].code` es `assistant-note-empty` (ausente, vacía o solo espacios) o
             *     `assistant-note-too-long` (más de 500 caracteres tras el recorte). Un `matterId` que
             *     no es UUID agrega un elemento con `field: matterId`. No se invoca al proveedor.
             */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            401: components["responses"]["Unauthorized"];
            403: components["responses"]["Forbidden"];
            /**
             * @description `code` = `feature-disabled` si el indicador `descriptionAssistant` está apagado
             *     (cualquier rol, se evalúa antes del rol), o `code` = `not-found` si `matterId` no
             *     corresponde a un asunto existente.
             */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
            500: components["responses"]["InternalError"];
            /**
             * @description El proveedor no respondió en el tiempo límite, falló o devolvió un texto vacío
             *     (`code` = `assistant-unavailable`). La descripción puede escribirse manualmente.
             */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    /**
                     * @example {
                     *       "type": "https://asuntos-horas.example/problems/assistant-unavailable",
                     *       "title": "Asistente no disponible",
                     *       "status": 503,
                     *       "detail": "El asistente no está disponible en este momento; puedes escribir la descripción manualmente.",
                     *       "code": "assistant-unavailable",
                     *       "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
                     *     }
                     */
                    "application/problem+json": components["schemas"]["Problem"];
                };
            };
        };
    };
}
