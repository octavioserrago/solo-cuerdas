# Arquitectura general — Solo Cuerdas

Visión de sistema completo, no solo del backend. Para el detalle de
implementación del backend día a día, ver `backend/ARCHITECTURE.md`. Para el
modelo de datos completo, ver `der-solo-cuerdas.mermaid` (este mismo
directorio) — es la fuente de verdad del diseño de entidades y tiene que
reflejar siempre lo que hay migrado en `supabase/migrations/`.

## Por qué existe este documento

El proyecto va a tener varios clientes (Android, web, landing) consumiendo el
mismo backend y la misma base, y se desarrolla en sesiones/ramas separadas en
el tiempo. Sin una referencia única que fije las reglas, cada sesión corre el
riesgo de resolver el mismo problema de una forma distinta — dónde vive la
lógica de negocio, cómo se valida un dato, qué hace cada capa — y terminar con
un sistema inconsistente entre ramas. Este documento es esa referencia: si una
decisión nueva lo contradice, se actualiza este documento como parte del mismo
cambio, no se lo ignora.

## 1. Mapa del sistema

```
┌─────────┐  ┌─────────┐  ┌──────────┐
│ Android │  │   Web    │  │ Landing  │   clientes (ninguno creado todavía —
│(Kotlin) │  │ (React)  │  │ (Astro)  │   iOS fuera del MVP)
└────┬────┘  └────┬─────┘  └────┬─────┘
     │            │              │
     │  Auth, Realtime (messages), Storage público (listing-media)
     │            │              │
     └──────┬─────┴──────┬───────┘
            │ JWT         │
            ▼             ▼
     ┌─────────────┐  ┌──────────────────────┐
     │  Supabase    │  │  Spring Boot backend  │
     │ Auth·Storage │  │   (Resource Server)   │
     │  ·Realtime   │  │  TODA la lógica de     │
     └──────┬───────┘  │  negocio vive acá      │
            │           └──────────┬────────────┘
            │   mismo Postgres      │ JDBC directo (sin RLS)
            ▼                       ▼
     ┌──────────────────────────────────┐
     │     PostgreSQL (Supabase)         │
     │  13 tablas, RLS on, migraciones   │
     └────────────────────────────────────┘
```

## 2. Principios no negociables

Estos principios valen para todo el sistema, no solo para el backend actual.
Cualquier decisión futura (nuevo cliente, nueva feature, nueva integración)
se mide contra esto antes de implementarse:

1. **Toda la lógica de negocio vive en el backend — nunca en un cliente ni en
   la base.** Cupos por plan, quién puede calificar, moderación, autorización
   contextual: todo en Spring. Ningún cliente (Android, web, landing)
   reimplementa estas reglas; todos llaman a la misma API.
2. **Supabase es infraestructura, no lógica de negocio.** Se usa para lo que
   resuelve mejor que reimplementarlo: autenticación, storage de archivos,
   lectura realtime de mensajes. No se le agrega lógica de dominio — nada de
   Edge Functions con reglas de negocio, nada de lógica nueva en triggers más
   allá de lo ya decidido (contador de rating, alta de perfil al registrarse).
3. **El esquema lo gobiernan las migraciones SQL, nunca Hibernate.**
   `ddl-auto: validate`. Todo cambio de esquema es una migración nueva en
   `supabase/migrations/`; nunca se edita una ya aplicada.
4. **El DER (`der-solo-cuerdas.mermaid`) y el esquema migrado nunca se
   desincronizan.** Ninguna tabla o columna se agrega sin pasar primero por
   el diagrama. Si cambia el DER, la migración correspondiente va en el mismo
   cambio; si se migra algo, el DER se actualiza en el mismo cambio.
5. **El backend es el único componente con acceso completo a Postgres.** Se
   conecta directo vía JDBC, sin pasar por RLS. Todo otro cliente accede a
   Supabase solo para lo del punto 2 (auth, storage, realtime) — nunca
   directo a la base para leer o escribir datos de negocio.
6. **Todo cambio estructural se documenta en el mismo cambio que lo
   introduce, no después.** Nueva entidad, nuevo endpoint, cambio de
   seguridad, paquete nuevo → se actualiza `backend/ARCHITECTURE.md` (si es
   de backend) o este documento (si afecta al sistema completo) junto con el
   código, no en una pasada aparte "para después".
7. **Una feature que necesita entidades nuevas extiende el DER primero, como
   paso propio, antes de escribir código.** Evita que una idea se empiece a
   implementar a medio camino y quede una mezcla inconsistente de diseño
   viejo y nuevo.

## 3. Cómo evaluar si algo nuevo es "escalable" bajo estos principios

Antes de agregar algo, responder:

- ¿Esto duplica lógica que ya vive (o debería vivir) en el backend, en otro
  lugar — un cliente, una Edge Function, un trigger? Si sí: no.
- ¿Este cambio de esquema está reflejado en el DER? Si no: agregarlo primero.
- ¿Un cliente nuevo (web, por ejemplo) necesitaría reimplementar algo de esto
  para comportarse igual que Android? Si sí: esa lógica tiene que subir al
  backend, no quedarse en el cliente.
- ¿Esta decisión se puede revertir o extender sin reescribir lo que ya existe
  si mañana se suma un cliente o un socio de negocio nuevo (ej. una casa de
  música como partner)? Si la respuesta implica tocar la mayoría de las
  entidades actuales, repensar el diseño antes de implementarlo.

## 4. Decisiones ya tomadas (para no reabrir la discusión)

- Supabase Auth emite los JWT; el backend los valida como OAuth2 Resource
  Server y nunca emite tokens propios.
- API completamente stateless: sin sesiones ni cookies, CSRF deshabilitado.
- Identificadores técnicos en inglés; textos de usuario en español (detalle
  en `backend/claude.md`).
- Fuera del MVP: permutas, pagos dentro de la plataforma, envíos, iOS, red de
  luthiers verificadores.

## 5. Ideas evaluadas pero no incorporadas todavía

Registro de propuestas ya discutidas, para no re-debatirlas desde cero ni
perderlas de vista.

- **Casas de música como punto de encuentro verificado** (evaluado
  2026-10-01). Resuelve un problema validado por el relevamiento de usuarios
  ("no poder ver/probar el instrumento antes de comprar" es la preocupación
  de confianza más mencionada), pero la solución en sí (partnership con
  locales) no está en el DER actual y no fue validada con casas de música
  todavía. Requeriría entidades nuevas que hoy no existen: venue/local,
  reviews de venue (hoy `reviews` solo apunta a `profiles`), un mecanismo de
  verificación de que el encuentro ocurrió ahí, y una regla de descuento de
  suscripción por volumen de operaciones (no hay nada parecido a pricing por
  uso en `plans`/`subscriptions` hoy).
  **Decisión:** no incorporar todavía. Primero completar el CRUD del MVP
  core (listings, ventas, reviews, reports), que ya está modelado en el DER
  y migrado en Postgres pero sin ningún endpoint implementado más allá de
  `GET /api/users/me`. Si se retoma esta idea, diseñarla como una extensión
  propia del DER (punto 7 de la sección 2), no mezclada a mitad de camino
  del MVP core.
