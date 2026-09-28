-- =============================================================================
-- Solo Cuerdas — Migración inicial
-- Traduce el DER (der-solo-cuerdas.mermaid) a PostgreSQL / Supabase.
--
-- Criterio de acceso:
--   * La lógica de negocio vive en Spring Boot, que se conecta directo a
--     PostgreSQL y no está sujeto a RLS.
--   * Los clientes (Android, web) solo acceden directo a Supabase para
--     autenticación, lectura en tiempo real de mensajes y archivos públicos.
--   * Por eso RLS se activa en TODAS las tablas (deniega por defecto) y solo
--     se abren las lecturas que el tiempo real necesita.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Tipos enumerados
-- -----------------------------------------------------------------------------
create type public.rol_usuario               as enum ('usuario', 'moderador', 'admin');
create type public.estado_identidad          as enum ('sin_verificar', 'pendiente', 'verificado', 'rechazado');
create type public.metodo_verificacion       as enum ('manual', 'proveedor_externo');
create type public.estado_verificacion       as enum ('pendiente', 'aprobada', 'rechazada');
create type public.estado_suscripcion        as enum ('vigente', 'vencida', 'cancelada');
create type public.estado_conservacion       as enum ('nuevo', 'excelente', 'muy_bueno', 'bueno', 'para_reparar');
create type public.moneda                    as enum ('ARS', 'USD');
create type public.estado_publicacion        as enum ('borrador', 'activa', 'pausada', 'vendida', 'eliminada');
create type public.tipo_medio                as enum ('foto', 'audio', 'video');
create type public.estado_operacion          as enum ('pendiente_confirmacion', 'concretada', 'cancelada');
create type public.estado_denuncia           as enum ('abierta', 'resuelta', 'descartada');


-- -----------------------------------------------------------------------------
-- 2. Usuarios y confianza
-- -----------------------------------------------------------------------------
create table public.perfiles (
    id                       uuid primary key references auth.users (id) on delete cascade,
    nombre                   text,
    apellido                 text,
    alias                    text,
    telefono                 text,
    provincia                text,
    localidad                text,
    rol                      public.rol_usuario      not null default 'usuario',
    estado_verificacion      public.estado_identidad not null default 'sin_verificar',
    reputacion_promedio      numeric(3,2) not null default 0
                             check (reputacion_promedio between 0 and 5),
    cantidad_calificaciones  integer      not null default 0
                             check (cantidad_calificaciones >= 0),
    creado_en                timestamptz  not null default now(),

    constraint chk_alias_formato check (alias is null or alias ~ '^[A-Za-z0-9_.]{3,30}$')
);
comment on table public.perfiles is 'Extiende auth.users con los datos de negocio. Un mismo perfil compra y vende.';

-- El alias es único sin distinguir mayúsculas ("Octa" = "octa").
create unique index uq_perfiles_alias on public.perfiles (lower(alias)) where alias is not null;


create table public.verificaciones (
    id                    uuid primary key default gen_random_uuid(),
    perfil_id             uuid not null references public.perfiles (id) on delete cascade,
    metodo                public.metodo_verificacion not null,
    proveedor             text,
    referencia_externa    text,
    url_documento_frente  text,
    url_documento_dorso   text,
    url_selfie            text,
    estado                public.estado_verificacion not null default 'pendiente',
    revisado_por          uuid references public.perfiles (id),
    motivo_rechazo        text,
    creado_en             timestamptz not null default now(),
    revisado_en           timestamptz,

    -- Cada método exige sus propios datos.
    constraint chk_verificacion_metodo check (
        (metodo = 'proveedor_externo' and proveedor is not null and referencia_externa is not null)
        or
        (metodo = 'manual' and url_documento_frente is not null and url_selfie is not null)
    ),
    -- Un rechazo siempre se explica al usuario.
    constraint chk_verificacion_motivo check (estado <> 'rechazada' or motivo_rechazo is not null),
    -- Una solicitud resuelta tiene fecha de resolución.
    constraint chk_verificacion_resuelta check ((estado = 'pendiente') = (revisado_en is null))
);

-- Como máximo una solicitud pendiente por perfil.
create unique index uq_verificaciones_pendiente on public.verificaciones (perfil_id) where estado = 'pendiente';


-- -----------------------------------------------------------------------------
-- 3. Planes
-- -----------------------------------------------------------------------------
create table public.planes (
    id                         integer generated always as identity primary key,
    nombre                     text          not null unique,
    max_publicaciones_activas  integer       not null check (max_publicaciones_activas > 0),
    precio_mensual             numeric(12,2) not null default 0 check (precio_mensual >= 0),
    destacados_incluidos       integer       not null default 0 check (destacados_incluidos >= 0)
);


create table public.suscripciones (
    id            uuid primary key default gen_random_uuid(),
    perfil_id     uuid    not null references public.perfiles (id) on delete cascade,
    plan_id       integer not null references public.planes (id),
    estado        public.estado_suscripcion not null default 'vigente',
    fecha_inicio  timestamptz not null default now(),
    fecha_fin     timestamptz,  -- nulo = sin vencimiento (plan Gratis)

    constraint chk_suscripcion_fechas check (fecha_fin is null or fecha_fin > fecha_inicio)
);

-- Como máximo una suscripción vigente por perfil.
create unique index uq_suscripciones_vigente on public.suscripciones (perfil_id) where estado = 'vigente';


-- -----------------------------------------------------------------------------
-- 4. Catálogo y publicaciones
-- -----------------------------------------------------------------------------
create table public.categorias (
    id      integer generated always as identity primary key,
    nombre  text not null
);
create unique index uq_categorias_nombre on public.categorias (lower(nombre));


create table public.marcas (
    id      integer generated always as identity primary key,
    nombre  text not null
);
create unique index uq_marcas_nombre on public.marcas (lower(nombre));


create table public.publicaciones (
    id                   uuid primary key default gen_random_uuid(),
    vendedor_id          uuid    not null references public.perfiles (id),
    categoria_id         integer not null references public.categorias (id),
    marca_id             integer references public.marcas (id),  -- nulo: luthier o sin marca
    modelo               text,
    titulo               text    not null check (char_length(titulo) between 5 and 120),
    descripcion          text    check (char_length(descripcion) <= 5000),
    anio                 integer check (anio between 1700 and 2100),
    origen               text,
    numero_serie         text,
    estado_conservacion  public.estado_conservacion not null,
    precio               numeric(14,2) not null check (precio > 0),
    moneda               public.moneda not null,
    provincia            text not null,
    localidad            text not null,
    estado               public.estado_publicacion not null default 'borrador',
    destacada_hasta      timestamptz,
    creado_en            timestamptz not null default now()
);

create index ix_publicaciones_vendedor  on public.publicaciones (vendedor_id);
-- Búsqueda: solo interesan las activas, así que los índices son parciales.
create index ix_publicaciones_activas_categoria on public.publicaciones (categoria_id, creado_en desc) where estado = 'activa';
create index ix_publicaciones_activas_marca     on public.publicaciones (marca_id)                     where estado = 'activa';
create index ix_publicaciones_activas_ubicacion on public.publicaciones (provincia, localidad)          where estado = 'activa';


create table public.medios (
    id                    uuid primary key default gen_random_uuid(),
    publicacion_id        uuid not null references public.publicaciones (id) on delete cascade,
    tipo                  public.tipo_medio not null,
    url                   text    not null,
    orden                 integer not null check (orden >= 1),
    hash_perceptual       text,
    es_foto_verificacion  boolean not null default false,

    constraint uq_medios_orden unique (publicacion_id, orden),
    -- Solo una foto puede ser foto de verificación.
    constraint chk_medios_verificacion check (not es_foto_verificacion or tipo = 'foto'),
    -- El hash perceptual solo aplica a imágenes.
    constraint chk_medios_hash check (hash_perceptual is null or tipo = 'foto')
);

create index ix_medios_hash on public.medios (hash_perceptual) where hash_perceptual is not null;


-- -----------------------------------------------------------------------------
-- 5. Comunicación
-- -----------------------------------------------------------------------------
create table public.conversaciones (
    id              uuid primary key default gen_random_uuid(),
    publicacion_id  uuid not null references public.publicaciones (id),
    comprador_id    uuid not null references public.perfiles (id),
    creado_en       timestamptz not null default now(),

    -- Un interesado tiene una sola conversación por publicación.
    constraint uq_conversaciones_publicacion_comprador unique (publicacion_id, comprador_id)
);
create index ix_conversaciones_comprador on public.conversaciones (comprador_id);


create table public.mensajes (
    id               uuid primary key default gen_random_uuid(),
    conversacion_id  uuid not null references public.conversaciones (id) on delete cascade,
    emisor_id        uuid not null references public.perfiles (id),
    contenido        text not null check (char_length(btrim(contenido)) between 1 and 2000),
    leido            boolean not null default false,
    enviado_en       timestamptz not null default now()
);
create index ix_mensajes_conversacion on public.mensajes (conversacion_id, enviado_en);


-- -----------------------------------------------------------------------------
-- 6. Operaciones y moderación
-- -----------------------------------------------------------------------------
create table public.operaciones (
    id              uuid primary key default gen_random_uuid(),
    publicacion_id  uuid not null references public.publicaciones (id),
    comprador_id    uuid not null references public.perfiles (id),
    precio_final    numeric(14,2) not null check (precio_final > 0),
    moneda          public.moneda not null,
    estado          public.estado_operacion not null default 'pendiente_confirmacion',
    creado_en       timestamptz not null default now(),
    concretada_en   timestamptz,

    -- concretada_en se informa si y solo si la operación está concretada.
    constraint chk_operaciones_concretada check ((estado = 'concretada') = (concretada_en is not null))
);

-- Una publicación tiene como máximo una operación concretada y una pendiente a la vez.
create unique index uq_operaciones_concretada on public.operaciones (publicacion_id) where estado = 'concretada';
create unique index uq_operaciones_pendiente  on public.operaciones (publicacion_id) where estado = 'pendiente_confirmacion';
create index ix_operaciones_comprador on public.operaciones (comprador_id);


create table public.calificaciones (
    id              uuid primary key default gen_random_uuid(),
    operacion_id    uuid not null references public.operaciones (id),
    calificador_id  uuid not null references public.perfiles (id),
    calificado_id   uuid not null references public.perfiles (id),
    puntaje         smallint not null check (puntaje between 1 and 5),
    comentario      text check (char_length(comentario) <= 500),
    creado_en       timestamptz not null default now(),

    -- Cada parte califica una sola vez por operación.
    constraint uq_calificaciones_operacion_calificador unique (operacion_id, calificador_id),
    constraint chk_calificaciones_distintos check (calificador_id <> calificado_id)
);
create index ix_calificaciones_calificado on public.calificaciones (calificado_id);


create table public.denuncias (
    id                    uuid primary key default gen_random_uuid(),
    denunciante_id        uuid not null references public.perfiles (id),
    publicacion_id        uuid references public.publicaciones (id),
    perfil_denunciado_id  uuid references public.perfiles (id),
    motivo                text not null check (char_length(btrim(motivo)) between 5 and 1000),
    estado                public.estado_denuncia not null default 'abierta',
    resuelta_por          uuid references public.perfiles (id),
    creado_en             timestamptz not null default now(),

    -- Una denuncia apunta a una publicación o a un perfil.
    constraint chk_denuncias_objeto check (publicacion_id is not null or perfil_denunciado_id is not null),
    -- Una denuncia cerrada registra quién la resolvió.
    constraint chk_denuncias_resuelta check ((estado = 'abierta') = (resuelta_por is null))
);
create index ix_denuncias_abiertas on public.denuncias (creado_en) where estado = 'abierta';


-- -----------------------------------------------------------------------------
-- 7. Datos de referencia
-- (Van en la migración, no en seed.sql, porque la aplicación los necesita
--  también en producción.)
-- -----------------------------------------------------------------------------
-- Valores de Plus y Tienda PROVISORIOS: a definir con el modelo de negocio.
insert into public.planes (nombre, max_publicaciones_activas, precio_mensual, destacados_incluidos) values
    ('Gratis',  2, 0, 0),
    ('Plus',   10, 0, 2),
    ('Tienda', 50, 0, 10);

insert into public.categorias (nombre) values
    ('Guitarra eléctrica'),
    ('Guitarra acústica'),
    ('Guitarra clásica o criolla'),
    ('Bajo eléctrico'),
    ('Bajo acústico'),
    ('Violín'),
    ('Viola'),
    ('Violonchelo'),
    ('Contrabajo'),
    ('Ukelele'),
    ('Banjo'),
    ('Mandolina'),
    ('Charango'),
    ('Cavaquinho'),
    ('Otros instrumentos de cuerda');

insert into public.marcas (nombre) values
    ('Fender'), ('Squier'), ('Gibson'), ('Epiphone'), ('Ibanez'), ('Yamaha'),
    ('PRS'), ('Gretsch'), ('ESP'), ('LTD'), ('Jackson'), ('Schecter'),
    ('Music Man'), ('Rickenbacker'), ('Taylor'), ('Martin'), ('Takamine'),
    ('Cort'), ('Washburn'), ('Fonseca'), ('Gracia'), ('Stentor'), ('Cremona');


-- -----------------------------------------------------------------------------
-- 8. Funciones y triggers
-- -----------------------------------------------------------------------------

-- 8.1 Al registrarse un usuario en Supabase Auth, se crea su perfil y se le
--     asigna el plan Gratis.
create function public.crear_perfil_nuevo_usuario()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.perfiles (id) values (new.id);

    insert into public.suscripciones (perfil_id, plan_id)
    select new.id, p.id from public.planes p where p.nombre = 'Gratis';

    return new;
end;
$$;

create trigger tr_auth_usuario_creado
    after insert on auth.users
    for each row execute function public.crear_perfil_nuevo_usuario();


-- 8.2 Reputación: se recalcula desde CALIFICACIONES cada vez que cambian.
--     Se recalcula completa (en lugar de sumar/restar) para que nunca pueda
--     quedar desincronizada.
create function public.recalcular_reputacion()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_perfil uuid;
begin
    v_perfil := coalesce(new.calificado_id, old.calificado_id);

    update public.perfiles pf
       set reputacion_promedio     = coalesce(r.promedio, 0),
           cantidad_calificaciones = r.cantidad
      from (select round(avg(c.puntaje)::numeric, 2) as promedio,
                   count(*)::integer                 as cantidad
              from public.calificaciones c
             where c.calificado_id = v_perfil) r
     where pf.id = v_perfil;

    return null;
end;
$$;

create trigger tr_calificaciones_reputacion
    after insert or update or delete on public.calificaciones
    for each row execute function public.recalcular_reputacion();


-- 8.3 ¿El usuario autenticado participa en esta conversación?
--     security definer: consulta PUBLICACIONES sin depender de sus políticas.
create function public.es_participante_conversacion(p_conversacion_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
          from public.conversaciones c
          join public.publicaciones p on p.id = c.publicacion_id
         where c.id = p_conversacion_id
           and (select auth.uid()) in (c.comprador_id, p.vendedor_id)
    );
$$;

revoke execute on function public.es_participante_conversacion(uuid) from public, anon;
grant  execute on function public.es_participante_conversacion(uuid) to authenticated;


-- -----------------------------------------------------------------------------
-- 9. Seguridad a nivel de fila (RLS)
-- Activada en todas las tablas: sin política, nadie accede desde los clientes.
-- -----------------------------------------------------------------------------
alter table public.perfiles        enable row level security;
alter table public.verificaciones  enable row level security;
alter table public.planes          enable row level security;
alter table public.suscripciones   enable row level security;
alter table public.categorias      enable row level security;
alter table public.marcas          enable row level security;
alter table public.publicaciones   enable row level security;
alter table public.medios          enable row level security;
alter table public.conversaciones  enable row level security;
alter table public.mensajes        enable row level security;
alter table public.operaciones     enable row level security;
alter table public.calificaciones  enable row level security;
alter table public.denuncias       enable row level security;

-- Mensajería en tiempo real: cada participante lee solo sus conversaciones y
-- mensajes. El envío de mensajes pasa por el backend.
create policy "participantes_leen_conversacion"
    on public.conversaciones for select to authenticated
    using (public.es_participante_conversacion(id));

create policy "participantes_leen_mensajes"
    on public.mensajes for select to authenticated
    using (public.es_participante_conversacion(conversacion_id));

alter publication supabase_realtime add table public.mensajes;


-- -----------------------------------------------------------------------------
-- 10. Almacenamiento de archivos
-- Las subidas se hacen con URLs firmadas que genera el backend, por lo que no
-- se abren políticas de escritura a los clientes.
-- -----------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types) values
    ('publicaciones', 'publicaciones', true,  52428800,   -- 50 MB
        array['image/jpeg', 'image/png', 'image/webp', 'audio/mpeg', 'audio/mp4', 'video/mp4']),
    ('verificaciones', 'verificaciones', false, 10485760, -- 10 MB, privado
        array['image/jpeg', 'image/png', 'image/webp']);
