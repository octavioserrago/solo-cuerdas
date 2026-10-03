-- =============================================================================
-- Solo Cuerdas — Migración inicial
-- Traduce el DER (docs/der-solo-cuerdas.mermaid) a PostgreSQL / Supabase.
--
-- Convención: identificadores en inglés (tablas, columnas, tipos, funciones);
-- los datos visibles para el usuario (nombres de planes, categorías) en español.
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
create type public.user_role           as enum ('user', 'moderator', 'admin');
create type public.identity_status     as enum ('unverified', 'pending', 'verified', 'rejected');
create type public.verification_method as enum ('manual', 'external_provider');
create type public.verification_status as enum ('pending', 'approved', 'rejected');
create type public.subscription_status as enum ('active', 'expired', 'cancelled');
create type public.item_condition      as enum ('new', 'excellent', 'very_good', 'good', 'needs_repair');
create type public.currency            as enum ('ARS', 'USD');
create type public.listing_status      as enum ('draft', 'active', 'paused', 'sold', 'deleted');
create type public.media_type          as enum ('photo', 'audio', 'video');
create type public.sale_status         as enum ('pending_confirmation', 'completed', 'cancelled');
create type public.report_status       as enum ('open', 'resolved', 'dismissed');


-- -----------------------------------------------------------------------------
-- 2. Usuarios y confianza
-- -----------------------------------------------------------------------------
create table public.profiles (
    id               uuid primary key references auth.users (id) on delete cascade,
    first_name       text,
    last_name        text,
    username         text,
    phone            text,
    province         text,
    city             text,
    role             public.user_role       not null default 'user',
    identity_status  public.identity_status not null default 'unverified',
    rating_average   numeric(3,2) not null default 0 check (rating_average between 0 and 5),
    rating_count     integer      not null default 0 check (rating_count >= 0),
    created_at       timestamptz  not null default now(),

    constraint chk_profiles_username_format check (username is null or username ~ '^[A-Za-z0-9_.]{3,30}$')
);
comment on table public.profiles is 'Extiende auth.users con los datos de negocio. Un mismo perfil compra y vende.';

-- El username es único sin distinguir mayúsculas ("Octa" = "octa").
create unique index uq_profiles_username on public.profiles (lower(username)) where username is not null;


create table public.identity_verifications (
    id                  uuid primary key default gen_random_uuid(),
    profile_id          uuid not null references public.profiles (id) on delete cascade,
    method              public.verification_method not null,
    provider            text,
    external_reference  text,
    document_front_url  text,
    document_back_url   text,
    selfie_url          text,
    status              public.verification_status not null default 'pending',
    reviewed_by         uuid references public.profiles (id),
    rejection_reason    text,
    created_at          timestamptz not null default now(),
    reviewed_at         timestamptz,

    -- Cada método exige sus propios datos.
    constraint chk_verifications_method check (
        (method = 'external_provider' and provider is not null and external_reference is not null)
        or
        (method = 'manual' and document_front_url is not null and selfie_url is not null)
    ),
    -- Un rechazo siempre se explica al usuario.
    constraint chk_verifications_rejection check (status <> 'rejected' or rejection_reason is not null),
    -- Una solicitud resuelta tiene fecha de resolución.
    constraint chk_verifications_reviewed check ((status = 'pending') = (reviewed_at is null))
);

-- Como máximo una solicitud pendiente por perfil.
create unique index uq_verifications_pending on public.identity_verifications (profile_id) where status = 'pending';


-- -----------------------------------------------------------------------------
-- 3. Planes
-- -----------------------------------------------------------------------------
create table public.plans (
    id                  integer generated always as identity primary key,
    code                text          not null unique,  -- identificador estable usado por el código
    name                text          not null,         -- nombre comercial visible al usuario
    max_active_listings integer       not null check (max_active_listings > 0),
    monthly_price       numeric(12,2) not null default 0 check (monthly_price >= 0),
    featured_included   integer       not null default 0 check (featured_included >= 0)
);


create table public.subscriptions (
    id          uuid primary key default gen_random_uuid(),
    profile_id  uuid    not null references public.profiles (id) on delete cascade,
    plan_id     integer not null references public.plans (id),
    status      public.subscription_status not null default 'active',
    starts_at   timestamptz not null default now(),
    ends_at     timestamptz,  -- nulo = sin vencimiento (plan gratuito)

    constraint chk_subscriptions_dates check (ends_at is null or ends_at > starts_at)
);

-- Como máximo una suscripción activa por perfil.
create unique index uq_subscriptions_active on public.subscriptions (profile_id) where status = 'active';


-- -----------------------------------------------------------------------------
-- 4. Catálogo y publicaciones
-- -----------------------------------------------------------------------------
create table public.categories (
    id    integer generated always as identity primary key,
    name  text not null
);
create unique index uq_categories_name on public.categories (lower(name));


create table public.brands (
    id    integer generated always as identity primary key,
    name  text not null
);
create unique index uq_brands_name on public.brands (lower(name));


create table public.listings (
    id                uuid primary key default gen_random_uuid(),
    seller_id         uuid    not null references public.profiles (id),
    category_id       integer not null references public.categories (id),
    brand_id          integer references public.brands (id),  -- nulo: luthier o sin marca
    model             text,
    title             text    not null check (char_length(title) between 5 and 120),
    description       text    check (char_length(description) <= 5000),
    manufacture_year  integer check (manufacture_year between 1700 and 2100),
    origin            text,
    serial_number     text,
    item_condition    public.item_condition not null,
    price             numeric(14,2) not null check (price > 0),
    currency          public.currency not null,
    province          text not null,
    city              text not null,
    status            public.listing_status not null default 'draft',
    featured_until    timestamptz,
    created_at        timestamptz not null default now()
);

create index ix_listings_seller on public.listings (seller_id);
-- Búsqueda: solo interesan las activas, así que los índices son parciales.
create index ix_listings_active_category on public.listings (category_id, created_at desc) where status = 'active';
create index ix_listings_active_brand    on public.listings (brand_id)                     where status = 'active';
create index ix_listings_active_location on public.listings (province, city)               where status = 'active';


create table public.listing_media (
    id                     uuid primary key default gen_random_uuid(),
    listing_id             uuid not null references public.listings (id) on delete cascade,
    media_type             public.media_type not null,
    url                    text    not null,
    sort_order             integer not null check (sort_order >= 1),
    perceptual_hash        text,
    is_verification_photo  boolean not null default false,

    constraint uq_listing_media_order unique (listing_id, sort_order),
    -- Solo una foto puede ser foto de verificación.
    constraint chk_listing_media_verification check (not is_verification_photo or media_type = 'photo'),
    -- El hash perceptual solo aplica a imágenes.
    constraint chk_listing_media_hash check (perceptual_hash is null or media_type = 'photo')
);

create index ix_listing_media_hash on public.listing_media (perceptual_hash) where perceptual_hash is not null;


-- -----------------------------------------------------------------------------
-- 5. Comunicación
-- -----------------------------------------------------------------------------
create table public.conversations (
    id          uuid primary key default gen_random_uuid(),
    listing_id  uuid not null references public.listings (id),
    buyer_id    uuid not null references public.profiles (id),
    created_at  timestamptz not null default now(),

    -- Un interesado tiene una sola conversación por publicación.
    constraint uq_conversations_listing_buyer unique (listing_id, buyer_id)
);
create index ix_conversations_buyer on public.conversations (buyer_id);


create table public.messages (
    id               uuid primary key default gen_random_uuid(),
    conversation_id  uuid not null references public.conversations (id) on delete cascade,
    sender_id        uuid not null references public.profiles (id),
    content          text not null check (char_length(btrim(content)) between 1 and 2000),
    is_read          boolean not null default false,
    sent_at          timestamptz not null default now()
);
create index ix_messages_conversation on public.messages (conversation_id, sent_at);


-- -----------------------------------------------------------------------------
-- 6. Ventas y moderación
-- -----------------------------------------------------------------------------
create table public.sales (
    id            uuid primary key default gen_random_uuid(),
    listing_id    uuid not null references public.listings (id),
    buyer_id      uuid not null references public.profiles (id),
    final_price   numeric(14,2) not null check (final_price > 0),
    currency      public.currency not null,
    status        public.sale_status not null default 'pending_confirmation',
    created_at    timestamptz not null default now(),
    completed_at  timestamptz,

    -- completed_at se informa si y solo si la venta está concretada.
    constraint chk_sales_completed check ((status = 'completed') = (completed_at is not null))
);

-- Una publicación tiene como máximo una venta concretada y una pendiente a la vez.
create unique index uq_sales_completed on public.sales (listing_id) where status = 'completed';
create unique index uq_sales_pending   on public.sales (listing_id) where status = 'pending_confirmation';
create index ix_sales_buyer on public.sales (buyer_id);


create table public.reviews (
    id           uuid primary key default gen_random_uuid(),
    sale_id      uuid not null references public.sales (id),
    reviewer_id  uuid not null references public.profiles (id),
    reviewee_id  uuid not null references public.profiles (id),
    rating       smallint not null check (rating between 1 and 5),
    comment      text check (char_length(comment) <= 500),
    created_at   timestamptz not null default now(),

    -- Cada parte califica una sola vez por venta.
    constraint uq_reviews_sale_reviewer unique (sale_id, reviewer_id),
    constraint chk_reviews_different_parties check (reviewer_id <> reviewee_id)
);
create index ix_reviews_reviewee on public.reviews (reviewee_id);


create table public.reports (
    id                   uuid primary key default gen_random_uuid(),
    reporter_id          uuid not null references public.profiles (id),
    listing_id           uuid references public.listings (id),
    reported_profile_id  uuid references public.profiles (id),
    reason               text not null check (char_length(btrim(reason)) between 5 and 1000),
    status               public.report_status not null default 'open',
    resolved_by          uuid references public.profiles (id),
    created_at           timestamptz not null default now(),

    -- Una denuncia apunta a una publicación o a un perfil.
    constraint chk_reports_target check (listing_id is not null or reported_profile_id is not null),
    -- Una denuncia cerrada registra quién la resolvió.
    constraint chk_reports_resolved check ((status = 'open') = (resolved_by is null))
);
create index ix_reports_open on public.reports (created_at) where status = 'open';


-- -----------------------------------------------------------------------------
-- 7. Datos de referencia
-- (Van en la migración, no en seed.sql, porque la aplicación los necesita
--  también en producción. Los textos visibles al usuario van en español.)
-- -----------------------------------------------------------------------------
-- Valores de Plus y Tienda PROVISORIOS: a definir con el modelo de negocio.
insert into public.plans (code, name, max_active_listings, monthly_price, featured_included) values
    ('free',  'Gratis',  2, 0, 0),
    ('plus',  'Plus',   10, 0, 2),
    ('store', 'Tienda', 50, 0, 10);

insert into public.categories (name) values
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

insert into public.brands (name) values
    ('Fender'), ('Squier'), ('Gibson'), ('Epiphone'), ('Ibanez'), ('Yamaha'),
    ('PRS'), ('Gretsch'), ('ESP'), ('LTD'), ('Jackson'), ('Schecter'),
    ('Music Man'), ('Rickenbacker'), ('Taylor'), ('Martin'), ('Takamine'),
    ('Cort'), ('Washburn'), ('Fonseca'), ('Gracia'), ('Stentor'), ('Cremona');


-- -----------------------------------------------------------------------------
-- 8. Funciones y triggers
-- -----------------------------------------------------------------------------

-- 8.1 Al registrarse un usuario en Supabase Auth, se crea su perfil y se le
--     asigna el plan gratuito.
create function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profiles (id) values (new.id);

    insert into public.subscriptions (profile_id, plan_id)
    select new.id, p.id from public.plans p where p.code = 'free';

    return new;
end;
$$;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();


-- 8.2 Reputación: se recalcula desde REVIEWS cada vez que cambian.
--     Se recalcula completa (en lugar de sumar/restar) para que nunca pueda
--     quedar desincronizada.
create function public.recalculate_rating()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_profile uuid;
begin
    v_profile := coalesce(new.reviewee_id, old.reviewee_id);

    update public.profiles pr
       set rating_average = coalesce(r.average, 0),
           rating_count   = r.total
      from (select round(avg(rv.rating)::numeric, 2) as average,
                   count(*)::integer                 as total
              from public.reviews rv
             where rv.reviewee_id = v_profile) r
     where pr.id = v_profile;

    return null;
end;
$$;

create trigger on_review_changed
    after insert or update or delete on public.reviews
    for each row execute function public.recalculate_rating();


-- 8.3 ¿El usuario autenticado participa en esta conversación?
--     security definer: consulta LISTINGS sin depender de sus políticas.
create function public.is_conversation_participant(p_conversation_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
          from public.conversations c
          join public.listings l on l.id = c.listing_id
         where c.id = p_conversation_id
           and (select auth.uid()) in (c.buyer_id, l.seller_id)
    );
$$;

revoke execute on function public.is_conversation_participant(uuid) from public, anon;
grant  execute on function public.is_conversation_participant(uuid) to authenticated;


-- -----------------------------------------------------------------------------
-- 9. Seguridad a nivel de fila (RLS)
-- Activada en todas las tablas: sin política, nadie accede desde los clientes.
-- -----------------------------------------------------------------------------
alter table public.profiles               enable row level security;
alter table public.identity_verifications enable row level security;
alter table public.plans                  enable row level security;
alter table public.subscriptions          enable row level security;
alter table public.categories             enable row level security;
alter table public.brands                 enable row level security;
alter table public.listings               enable row level security;
alter table public.listing_media          enable row level security;
alter table public.conversations          enable row level security;
alter table public.messages               enable row level security;
alter table public.sales                  enable row level security;
alter table public.reviews                enable row level security;
alter table public.reports                enable row level security;

-- Mensajería en tiempo real: cada participante lee solo sus conversaciones y
-- mensajes. El envío de mensajes pasa por el backend.
create policy "participants_read_conversation"
    on public.conversations for select to authenticated
    using (public.is_conversation_participant(id));

create policy "participants_read_messages"
    on public.messages for select to authenticated
    using (public.is_conversation_participant(conversation_id));

alter publication supabase_realtime add table public.messages;


-- -----------------------------------------------------------------------------
-- 10. Almacenamiento de archivos
-- Las subidas se hacen con URLs firmadas que genera el backend, por lo que no
-- se abren políticas de escritura a los clientes.
-- -----------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types) values
    ('listing-media', 'listing-media', true, 52428800,             -- 50 MB
        array['image/jpeg', 'image/png', 'image/webp', 'audio/mpeg', 'audio/mp4', 'video/mp4']),
    ('identity-documents', 'identity-documents', false, 10485760,  -- 10 MB, privado
        array['image/jpeg', 'image/png', 'image/webp']);
