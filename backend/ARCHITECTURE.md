# Arquitectura del backend — Solo Cuerdas

Documentación técnica del backend tal como está implementado hoy. Se actualiza a
medida que el código cambia: es la referencia para entender el estado real del
sistema sin tener que releer todo el código fuente.

Para convenciones de trabajo y contexto de producto, ver `backend/claude.md`
(no versionado). Este archivo sí se versiona: es documentación de ingeniería,
no notas personales.

Para los principios de arquitectura que valen para **todo** el sistema (no
solo el backend) y deben respetarse en cualquier rama/sesión nueva, ver
`docs/ARCHITECTURE.md`.

## 1. Visión general

```
┌──────────────┐        JWT (Supabase Auth)        ┌─────────────────────┐
│  Clientes     │ ───────────────────────────────▶ │  Spring Boot backend │
│ (Android/Web) │                                   │  (Resource Server)   │
└──────┬───────┘                                   └──────────┬──────────┘
       │                                                       │
       │ Auth, Realtime (messages),                            │ JDBC directo
       │ Storage público (listing-media)                       │ (sin RLS)
       ▼                                                       ▼
┌──────────────────────────────┐                     ┌──────────────────────┐
│           Supabase           │◀────────────────────│      PostgreSQL       │
│ Auth · Storage · Realtime    │   mismo Postgres     │  (13 tablas, RLS on) │
└──────────────────────────────┘                     └──────────────────────┘
```

Decisiones clave (el porqué):

- **El backend es la única pieza con permisos completos sobre la base.** Se
  conecta directo a PostgreSQL vía JDBC y no pasa por las políticas RLS. Toda
  la lógica de negocio y autorización (cupos por plan, quién puede calificar,
  moderación) vive en Spring, no en la base.
- **Los clientes nunca hablan con Postgres directamente.** Solo usan el SDK de
  Supabase para: autenticación (login/signup), lectura realtime de `messages`,
  y lectura pública del bucket `listing-media`. Por eso RLS está activo en las
  13 tablas con deny-by-default, y solo hay políticas de *lectura* para
  conversations/messages de sus propios participantes.
- **API stateless.** El backend valida el JWT que emite Supabase Auth
  (OAuth2 Resource Server, JWKS del proyecto) en cada request. No hay
  sesiones ni cookies, por eso CSRF está deshabilitado.
- **El esquema lo gobiernan las migraciones SQL, no Hibernate.** `ddl-auto:
  validate` — cuando existan entidades JPA, Hibernate solo va a comprobar que
  coincidan con lo que ya migró Supabase. Nunca génera ni altera tablas.

## 2. Stack y versiones

| Pieza | Versión / detalle |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 (`spring-boot-starter-parent`) |
| Build | Maven (`mvnw`) |
| Persistencia | `spring-boot-starter-data-jpa` (sin entidades propias todavía) |
| Seguridad | `spring-boot-starter-security` + `-oauth2-resource-server` |
| Validación | `spring-boot-starter-validation` (sin uso todavía) |
| Web | `spring-boot-starter-webmvc` |
| Driver DB | `org.postgresql:postgresql` (runtime) |
| Base de datos | PostgreSQL 17 vía Supabase (región São Paulo) |
| Tests | starters `-test` de jpa/security/oauth2/validation/webmvc, ninguno usado aún más allá del test de contexto generado |

## 3. Estructura de paquetes

```
ar.solocuerdas.backend
├── BackendApplication.java   — entry point (@SpringBootApplication)
├── config/
│   ├── SecurityConfig.java       — filter chain de seguridad
│   └── ApiExceptionHandler.java  — @RestControllerAdvice (errores → status HTTP)
├── users/
│   ├── UserController.java          — GET /me, PATCH /me, GET /{id}
│   ├── Profile.java                  — entidad JPA de `profiles`
│   ├── ProfileRepository.java        — JpaRepository<Profile, UUID>
│   ├── ProfileResponse.java          — record de respuesta de /me (reemplaza al Map)
│   ├── PublicProfileResponse.java    — record de respuesta de /{id} (subconjunto público)
│   └── UpdateProfileRequest.java     — record de request de PATCH /me
├── listings/
│   ├── ListingController.java        — POST /, GET /me, PATCH /{id} (requieren JWT)
│   ├── PublicListingController.java  — GET /, GET /{id} (sin auth, bajo /api/public/listings)
│   ├── ListingMediaController.java    — POST /{listingId}/media, POST /{listingId}/media/{id}/confirm
│   ├── Listing.java                   — entidad JPA de `listings`
│   ├── ListingMedia.java               — entidad JPA de `listing_media`
│   ├── ListingRepository.java         — JpaRepository<Listing, UUID> + queries derivadas
│   ├── ListingMediaRepository.java     — JpaRepository<ListingMedia, UUID>
│   ├── MediaStorageClient.java         — interfaz (abstrae Supabase Storage)
│   ├── SupabaseMediaStorageClient.java — implementación real (RestClient + service role key)
│   ├── ListingResponse.java           — record de respuesta (único, público = privado del dueño)
│   ├── MediaUploadResponse.java / MediaResponse.java
│   ├── CreateListingRequest.java / CreateMediaRequest.java
│   ├── UpdateListingRequest.java       — record de request de PATCH
│   └── ListingQuotaExceededException.java — 409 cuando se llega al tope del plan
├── plans/
│   ├── Plan.java                 — entidad JPA mínima de `plans` (solo id + max_active_listings)
│   ├── PlanRepository.java       — JpaRepository<Plan, Integer>
│   ├── Subscription.java         — entidad JPA mínima de `subscriptions`
│   └── SubscriptionRepository.java — JpaRepository<Subscription, UUID> + findByProfileIdAndStatus
├── sales/
│   ├── SaleController.java        — POST /, POST /{id}/confirm
│   ├── Sale.java                   — entidad JPA de `sales`
│   ├── SaleRepository.java         — JpaRepository<Sale, UUID>
│   ├── SaleResponse.java           — record de respuesta
│   ├── CreateSaleRequest.java      — record de request de POST
│   └── ConfirmSaleRequest.java     — record de request de POST /{id}/confirm
├── reviews/
│   ├── ReviewController.java      — POST /
│   ├── Review.java                 — entidad JPA de `reviews` (rating: Short, es smallint)
│   ├── ReviewRepository.java       — JpaRepository<Review, UUID>
│   ├── ReviewResponse.java         — record de respuesta
│   └── CreateReviewRequest.java    — record de request de POST
├── reports/
│   ├── ReportController.java       — POST /, PATCH /{id} (resolver)
│   ├── Report.java                  — entidad JPA de `reports`
│   ├── ReportRepository.java        — JpaRepository<Report, UUID>
│   ├── ReportResponse.java          — record de respuesta
│   ├── CreateReportRequest.java     — record de request de POST
│   └── ResolveReportRequest.java    — record de request de PATCH /{id}
└── catalog/
    ├── CatalogController.java      — GET /api/public/categories, GET /api/public/brands
    ├── Category.java / Brand.java   — entidades JPA de solo lectura
    ├── CategoryRepository.java / BrandRepository.java
    └── CategoryResponse.java / BrandResponse.java
```

Paquete base: `ar.solocuerdas.backend`. Un paquete por dominio de negocio
(`users`, y los que se sumen: `listings`, `conversations`, etc.), no por capa
técnica (nada de `controllers/`, `services/` a nivel raíz).

## 4. Seguridad (`SecurityConfig`)

- `SessionCreationPolicy.STATELESS`: no hay `HttpSession`.
- CSRF deshabilitado (no aplica sin cookies/sesión).
- `/api/public/**` → sin autenticación. Todo lo demás requiere JWT válido.
- `oauth2ResourceServer().jwt()`: valida el token contra
  `${SUPABASE_URL}/auth/v1` (issuer) y su JWKS —configurado en
  `application.yml`, valores reales en `.env` (no versionado).
- **`JwtDecoder` propio, explícito** (bean `jwtDecoder()`): Supabase firma los
  JWT con **ES256** (clave asimétrica), no con un secreto compartido. El
  decoder que arma Spring Boot automáticamente a partir de las propiedades
  sueltas de `application.yml` asume **RS256** por defecto y rechaza
  cualquier otro algoritmo (error real que apareció recién al probar con un
  JWT real contra el servidor: "Another algorithm expected, or no matching
  key(s) found" — los tests con JWT simulado no lo detectan, porque no pasan
  por este decoder). Por eso se construye el decoder a mano con
  `NimbusJwtDecoder.withJwkSetUri(...).jwsAlgorithm(SignatureAlgorithm.ES256)`,
  y se le vuelve a agregar manualmente la validación de `issuer`
  (`JwtValidators.createDefaultWithIssuer(...)`) — al declarar un `JwtDecoder`
  propio, Spring Boot deja de armar el suyo (que incluía esa validación).
- No hay autorización por rol todavía (`hasRole(...)`): cualquier JWT válido
  pasa `authenticated()`. Las reglas de negocio contextuales (rol
  `moderator`/`admin`, dueño del recurso) se resuelven en cada controller/
  service a medida que se implementan, leyendo el claim `role` del JWT.

Flujo de una request autenticada:

1. Cliente manda `Authorization: Bearer <jwt>` (token de Supabase Auth).
2. Spring Security valida firma + issuer contra el JWKS de Supabase.
3. Si es válido, se puebla el `SecurityContext` con un principal `Jwt`.
4. El controller lo recibe con `@AuthenticationPrincipal Jwt jwt` y lee claims
   (`jwt.getSubject()` = id de `auth.users`/`profiles`, `email`, `role`).

## 5. Endpoints actuales

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| GET | `/api/users/me` | JWT requerido | Devuelve el `ProfileResponse` del usuario autenticado: datos de `profiles` + `email` (del JWT, no vive en la tabla). |
| PATCH | `/api/users/me` | JWT requerido | Completa/edita el propio perfil. Actualización parcial: solo pisa los campos presentes en el body (`UpdateProfileRequest`). `username` inválido → `400`; `username` duplicado → `409` (vía `ApiExceptionHandler`). |
| GET | `/api/users/{id}` | JWT requerido | Perfil público de **otro** usuario: `PublicProfileResponse` (subconjunto de campos — ver sección 7, nunca `phone`/`role`/`email`). `id` inexistente → `404`. |
| POST | `/api/listings` | JWT requerido | Crea una publicación, siempre en `active`. `seller_id` sale del JWT. Si el vendedor llegó al cupo de su plan (`plans.max_active_listings`) → `409` con mensaje explicando las dos salidas (pausar una existente o mejorar de plan). |
| GET | `/api/listings/me` | JWT requerido | Las publicaciones propias, en **cualquier** estado (necesario para poder elegir cuál pausar). |
| PATCH | `/api/listings/{id}` | JWT requerido, dueño | Edita campos propios y/o cambia `status` entre `active`⇄`paused` (cualquier otro valor → `400`; no es dueño → `403`; reactivar en el tope del cupo → `409`, mismo chequeo que el `POST`). |
| GET | `/api/public/listings/{id}` | **Sin auth** | Detalle público — solo si `status = 'active'` (el filtro va en la query, no en un `if` posterior); si no, `404` sin distinguir "no existe" de "no está activa". |
| GET | `/api/public/listings` | **Sin auth** | Lista de publicaciones `active`, con filtros opcionales por query param: `categoryId`, `brandId`, `province`, `city`, `minPrice`, `maxPrice` (todos combinables, ninguno obligatorio). Sin paginación ni orden (destacadas primero, más nuevas primero) todavía. |
| POST | `/api/sales` | JWT requerido, dueño de la publicación | Crea una venta `pending_confirmation` para un comprador puntual. Genera un código de 6 dígitos que el vendedor le pasa al comprador en persona. Publicación no `active` → `400`; ya hay venta pendiente/completada para esa publicación → `409`. |
| POST | `/api/sales/{id}/confirm` | JWT requerido, debe ser el comprador | El comprador escribe el código. Si coincide: la venta pasa a `completed` (y se borra el código), y la publicación pasa a `status = sold`. Código incorrecto → `400`; venta ya no está `pending_confirmation` → `400`; no sos el comprador → `403`. |
| POST | `/api/reviews` | JWT requerido, parte de la venta | Califica a la otra parte de una venta `completed` (`saleId`, `rating`, `comment` opcional). El `revieweeId` lo calcula el backend: si sos el comprador, calificás al vendedor (vía `listings.seller_id`), y viceversa. Venta no `completed` → `400`; no participaste de esa venta → `403`; ya la calificaste → `409`. La reputación (`profiles.rating_average`/`rating_count`) se actualiza sola, vía el trigger de la base — no hay lógica de backend para eso. |
| POST | `/api/reports` | JWT requerido | Denuncia una publicación y/o un perfil (`listingId`/`reportedProfileId`, al menos uno; `reason`). Siempre arranca en `open`. Ningún objetivo indicado → `400`. |
| PATCH | `/api/reports/{id}` | JWT requerido, **moderador o admin** | Resuelve una denuncia (`status`: `resolved`/`dismissed`), registrando quién la resolvió. No sos moderador/admin → `403` (primera vez que el backend chequea **rol**, no solo dueño del recurso). Denuncia ya no `open` → `400`; valor de `status` inválido → `400`. |
| GET | `/api/public/categories` | **Sin auth** | Catálogo de categorías (seedeado, solo lectura). |
| GET | `/api/public/brands` | **Sin auth** | Catálogo de marcas (seedeado, solo lectura). |
| POST | `/api/listings/{listingId}/media` | JWT requerido, dueño | Reserva una subida: crea la fila en `listing_media` (`moderation_status = pending`) y devuelve una URL firmada de Supabase Storage para que el cliente suba el archivo directo (no pasa por el backend). |
| POST | `/api/listings/{listingId}/media/{mediaId}/confirm` | JWT requerido, dueño | El cliente avisa que terminó de subir. Corre la revisión de moderación — **hoy un stub que siempre aprueba** (ver sección 7 y `docs/ARCHITECTURE.md` sección 5) — y pasa a `approved` (o `rejected` + borra el archivo de Storage, rama ya escrita aunque hoy nunca se dispare). `mediaId` que no pertenece a `listingId` → `404` (ver nota de seguridad abajo); ya no está `pending` → `400`. |

Todos los campos de `listings` se consideraron públicos a propósito (incluido
`serial_number`, decisión explícita) — por eso `ListingResponse` es un único
record, reutilizado tanto para las rutas públicas como para `/me`: la
diferencia no está en qué campos se ven, sino en qué publicaciones se pueden
ver (el público solo `active`; el dueño, cualquier estado).

Los tres devuelven `record` de Java, no `Map` — la deuda que había acá ya se
resolvió. `/{id}` requiere JWT igual que el resto (no está bajo
`/api/public/**`): hoy cualquier usuario autenticado puede ver el perfil
público de cualquier otro; si más adelante se necesita que sea accesible sin
login, es un cambio de `SecurityConfig`, no de este endpoint.

## 6. Persistencia

Primera entidad del proyecto: `Profile` (mapea `profiles`), con su
`ProfileRepository` (`JpaRepository<Profile, UUID>`, sin métodos propios:
alcanza con `findById`/`save`). `role` e `identity_status` (enums nativos de
Postgres) se mapean como `String` simple — un `AttributeConverter` a enum de
Java tipado queda para cuando haya lógica que compare contra esos valores.

**`DB_URL` necesita `?stringtype=unspecified`** (en `backend/.env`, no
versionado). Sin esto, cualquier `UPDATE`/`INSERT` que escriba un `String` de
Java en una columna de enum nativo de Postgres (`role`, `identity_status`, y
a futuro `listing_status`, `sale_status`, etc.) falla con *"column is of
type X but expression is of type character varying"* — el driver JDBC por
defecto declara los `String` como `varchar`, y Postgres no lo castea solo a
un enum custom. `stringtype=unspecified` hace que el driver no declare tipo,
y Postgres infiere el correcto por contexto. Se eligió arreglarlo acá (nivel
de conexión) y no con anotaciones por entidad (`@JdbcTypeCode`) — eso se
probó primero y no funcionó bien (terminó mandando el valor como binario) —
para que cubra automáticamente cualquier columna de enum nativo futura, sin
tener que repetir nada por entidad.

El resto de las tablas (`listings`, `sales`, etc.) todavía no tiene entidad.

`ddl-auto=validate` ya se ejercitó contra la base real: `BackendApplicationTests`
(`@SpringBootTest`) arranca el contexto completo contra Supabase, así que
cualquier mapeo de `Profile` que no coincida con la columna real rompe esa
suite — es, de hecho, el único "test de integración" que hay hoy (no apunta a
`Profile` a propósito, pero lo valida como efecto colateral de arrancar).

## 7. Modelo de datos (resumen)

Fuente de verdad: `supabase/migrations/20260928120000_initial_schema.sql`
(schema completo) y `docs/der-solo-cuerdas.mermaid` (diagrama). Resumen para
no tener que releer las ~420 líneas de SQL:

**Confianza y usuarios**
- `profiles` (PK = `auth.users.id`): nombre, `username` único (case-insensitive),
  `role` (`user|moderator|admin`), `identity_status`
  (`unverified|pending|verified|rejected`), `rating_average`/`rating_count`
  (calculados por trigger).
- `identity_verifications`: `method` (`manual|external_provider`) exige campos
  distintos según el método (chk); máx. una solicitud `pending` por perfil.

**Planes**
- `plans`: catálogo seedeado (`free`/`plus`/`store`), precios y límites
  **provisorios**.
- `subscriptions`: una activa por perfil (unique index parcial).

**Catálogo**
- `categories` (15 seedeadas), `brands` (23 seedeadas): nombre único
  case-insensitive.

**Publicaciones**
- `listings`: dueño (`seller_id`), categoría, marca opcional, condición,
  precio/moneda, ubicación, `status`
  (`draft|active|paused|sold|deleted`). Índices parciales optimizados para
  `status = 'active'` (por categoría, marca, ubicación).
- `listing_media`: fotos/audio/video, orden único por publicación,
  `perceptual_hash` (detección de duplicados, solo fotos, sin calcular
  todavía), `is_verification_photo` (solo fotos). `moderation_status`
  (agregado en `20261003030000_listing_media_moderation.sql`):
  `pending|approved|rejected`; solo lo `approved` debería mostrarse
  (todavía no hay ningún endpoint que devuelva media embebida en una
  publicación, así que hoy esto no se filtra en ningún lado — ver sección 9).

**Comunicación**
- `conversations`: una por (`listing_id`, `buyer_id`).
- `messages`: tabla agregada a `supabase_realtime` (los clientes la leen en
  vivo vía Supabase; el envío pasa por el backend).

**Ventas y reputación**
- `sales`: máx. una `completed` y una `pending_confirmation` por publicación.
  `confirmation_code` (agregado en `20261003020000_sales_confirmation_and_rating_fix.sql`):
  el vendedor lo genera al crear la venta, el comprador lo escribe para
  confirmarla — sin esto, cualquiera de las dos partes podría fabricar una
  venta sin que la otra participe.
- `reviews`: una por (`sale_id`, `reviewer_id`); `reviewer_id ≠ reviewee_id`.
- `reports`: apunta a una publicación y/o a un perfil (al menos uno).

**Funciones y triggers**
- `handle_new_user()`: al crearse un `auth.users`, crea su `profiles` y lo
  suscribe al plan `free`.
- `recalculate_rating()`: recalcula `rating_average`/`rating_count` del
  `reviewee` cada vez que cambian sus `reviews` (recálculo completo, no
  incremental, para que nunca quede desincronizado). Redefinida en
  `20261003020000_sales_confirmation_and_rating_fix.sql`: solo cuenta la
  **primera** review de cada `reviewer` hacia un mismo `reviewee` — evita
  que dos cuentas (o una misma persona con dos cuentas) se inflen la
  reputación repitiendo operaciones entre sí. Las reviews siguientes del
  mismo par se guardan, pero no suman al promedio/contador.
- `is_conversation_participant(uuid)`: `security definer`; usada por las
  policies de RLS para chequear si `auth.uid()` es comprador o vendedor de
  una conversación.

**RLS**: activado en las 13 tablas, deny-by-default. Únicas políticas hoy:
lectura de `conversations`/`messages` para sus participantes (soporte de
mensajería realtime). Todo lo demás se accede exclusivamente vía backend.

**Storage**: bucket `listing-media` (público, 50 MB, imagen/audio/video) y
`identity-documents` (privado, 10 MB, solo imágenes). Sin políticas de
escritura para clientes: las subidas van a través de URLs firmadas que
**todavía no están implementadas** en el backend.

## 8. Configuración (`application.yml` / `.env`)

`application.yml` importa `.env` (no versionado) para: `DB_URL`, `DB_USER`,
`DB_PASSWORD`, `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`. No hay profiles
de Spring (`dev`/`prod`) todavía — un solo `application.yml` para todo.

`SUPABASE_SERVICE_ROLE_KEY` (nuevo, para `listing_media`): a diferencia del
resto de las variables, no se referencia desde `application.yml` — se lee
directo con `@Value("${SUPABASE_SERVICE_ROLE_KEY}")` en
`SupabaseMediaStorageClient`, porque el `.env` importado como property
source deja cualquier variable disponible así, sin tener que declararla
primero en el yml (mismo mecanismo con el que `${SUPABASE_URL}` ya se usaba
dentro del yml). Es una clave con permisos de administrador sobre todo el
proyecto de Supabase — nunca debe llegar a ningún cliente (Android/web),
solo el backend la usa.

## 9. Implementado vs. pendiente

**Implementado**
- Esqueleto Spring Boot 4.1.1 / Java 21, conectado a Supabase Postgres.
- Seguridad: JWT de Supabase validado como Resource Server, stateless, con
  `JwtDecoder` explícito aceptando ES256.
- `GET`/`PATCH /api/users/me` (completar/editar perfil propio) y
  `GET /api/users/{id}` (perfil público, subconjunto de campos).
- CRUD de `listings` (ronda 1 — core, sin media/búsqueda/categorías):
  `POST`/`GET /me`/`PATCH /{id}` (autenticado) y `GET`/`GET /{id}`
  (público, bajo `/api/public/listings`). Cupo por plan (`max_active_listings`)
  chequeado al crear y al reactivar una pausada. Autorización por dueño
  (`403` si no sos el vendedor) — primer endpoint que lo necesitó.
- `sales` (ronda 1): `POST /api/sales` (el vendedor registra la venta,
  genera un código de 6 dígitos) y `POST /api/sales/{id}/confirm` (el
  comprador lo confirma; la publicación pasa a `sold`). Pensado contra
  fraude de a una sola parte (no alcanza con que el vendedor la marque
  solo); la colusión entre dos cuentas se ataca aparte, a nivel de
  reputación (ver sección 7, `recalculate_rating`).
- `reviews`: `POST /api/reviews` — califica a la otra parte de una venta
  `completed`. El `revieweeId` se calcula solo (comprador↔vendedor, vía
  `listings.seller_id`); venta no completada → `400`, no participaste →
  `403`, ya calificaste esa venta → `409`. La reputación se recalcula
  sola (trigger de la base).
- `reports`: `POST /api/reports` (denunciar una publicación y/o un
  perfil) y `PATCH /api/reports/{id}` (resolver — **moderador/admin**,
  `403` si no; primera autorización por **rol**, no por dueño del
  recurso, del proyecto).
- **MVP core completo**: `listings → sales → reviews → reports`, el
  orden que se había definido en `docs/ARCHITECTURE.md` sección 5. A
  partir de acá, lo que sigue son sub-proyectos que quedaron afuera a
  propósito (ver pendientes) o módulos nuevos (conversaciones/mensajes,
  verificación de identidad).
- Entidades JPA: `Profile`, `Listing`, `Sale`, `Review`, `Report`,
  `Plan`/`Subscription` (estas dos últimas mínimas, de solo lectura —
  ver sección 6).
- `ApiExceptionHandler` (`@RestControllerAdvice`): `DataIntegrityViolationException`
  → `409`, `NoSuchElementException` → `404`, `ListingQuotaExceededException`
  → `409`. `AccessDeniedException` (dueño/parte de la venta/rol) y
  status/código inválido (`ResponseStatusException`) se resuelven con el
  soporte nativo de Spring, sin entrada propia acá.
- Esquema completo migrado (13 tablas, enums, triggers, RLS, buckets) +
  una migración de evolución (`confirmation_code` en `sales`, fix de
  `recalculate_rating`).
- Probado de punta a punta contra el servidor real (no solo tests
  mockeados): `GET`/`PATCH /me`, todo `listings`, todo `sales` y todo
  `reviews` confirmados por Postman contra Supabase de verdad. `reports`,
  `catalog` y la búsqueda filtrada todavía solo probados con tests
  automatizados (Postman pendiente).
- `catalog`: `GET /api/public/categories` y `GET /api/public/brands` —
  sin auth, sin lógica, catálogos seedeados de solo lectura.
- Búsqueda/filtrado de `listings`: `GET /api/public/listings` acepta
  `categoryId`, `brandId`, `province`, `city`, `minPrice`, `maxPrice`
  (todos opcionales y combinables), vía una sola query con parámetros
  nulleables — sin Specifications ni Criteria API, mismo estilo que el
  resto de los repositorios del proyecto.
- Media de `listings` (ronda 1): `POST /{id}/media` (reserva, URL firmada
  de Supabase Storage) + `POST /{id}/media/{mediaId}/confirm` (confirma,
  corre moderación). Primera integración HTTP con un servicio externo
  (`MediaStorageClient`/`SupabaseMediaStorageClient`, nueva credencial
  `SUPABASE_SERVICE_ROLE_KEY`). Moderación con **stub que siempre aprueba**
  — a propósito, es el diseño aceptado para la tesis; la regla real para
  producción (cuarentena antes del bucket público) está documentada como
  no negociable en `docs/ARCHITECTURE.md` secciones 2 y 5, no implementada.
  **Fix de seguridad (detectado por revisión automática del commit, no en
  el diseño original):** `confirm` buscaba la media solo por `mediaId`, sin
  verificar que perteneciera al `listingId` del path — un dueño de
  cualquier publicación podía confirmar (o, a futuro, disparar el borrado
  de Storage de) media de una publicación ajena. Se corrigió escopeando la
  búsqueda (`findByIdAndListingId`) y de paso se sumó el chequeo de que la
  media siga `pending` antes de confirmarla (mismo patrón que `sales`).
- Tests: 50 (1 de contexto + 49 de controllers, con TDD — `@WebMvcTest` +
  repositorios y `MediaStorageClient` mockeados; la implementación real de
  Storage no tiene test automatizado — mismo criterio que Postgres real,
  se verificó a mano contra la API de Supabase antes de escribir el código,
  y falta la verificación manual end-to-end por Postman).

**Pendiente (próximos pasos típicos, no priorizados)**
- De `listings`: que `ListingResponse` devuelva la media aprobada de la
  publicación (hoy existe el endpoint de subida pero ninguna respuesta la
  expone todavía); paginación y orden (destacadas/más nuevas primero) en
  la búsqueda; compresión de video como capa intermedia (ver
  `docs/ARCHITECTURE.md` sección 5).
- De `sales`: cancelar una venta a mano, listar mis ventas (como
  comprador o vendedor).
- De `reviews`: listar las reviews individuales de un usuario (hoy solo
  se ve el promedio/contador en el perfil); validación de formato en
  `CreateReviewRequest` (`rating` 1-5, `comment` ≤500 — hoy dependen de
  los `check` de la base, igual que pasó al principio con `listings`).
- De `reports`: listar denuncias abiertas (hoy un moderador necesita el
  `id` de otra fuente, ej. Supabase Table Editor, para poder resolverla).
- Módulos nuevos: conversaciones/mensajes (con su particularidad de
  Realtime+RLS), verificación de identidad (`identity_verifications`).
- Atar `sales`/`reviews` a que ambas partes tengan `identity_status =
  verified` — refuerzo anti-colusión pensado para cuando exista el
  módulo de verificación de identidad.
- Test de integración real contra Postgres (Testcontainers) — hoy todo se
  prueba con el repositorio mockeado.
- `UpdateProfileRequest`/`UpdateListingRequest` no permiten vaciar un campo
  a propósito (`null` y "no enviado" se tratan igual).
- `status = 'deleted'` (baja definitiva) no está cubierto en `listings` —
  solo `active`⇄`paused`.
