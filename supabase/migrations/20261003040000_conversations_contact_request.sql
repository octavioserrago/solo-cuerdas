-- =============================================================================
-- Solicitud de contacto: gate del vendedor antes de habilitar mensajería.
--
-- - conversations gana status (pending/accepted/rejected) y contact_reason
--   (motivo fijo elegido por el comprador, no texto libre).
-- - reports gana conversation_id: cuando una denuncia nace de una conversación,
--   el moderador puede ir directo a leer los mensajes como prueba.
-- - blocked_buyers: bloqueo automático por vendedor, disparado por el backend
--   al crear una denuncia con conversation_id + reported_profile_id.
-- =============================================================================

create type public.conversation_status as enum ('pending', 'accepted', 'rejected');
create type public.contact_reason      as enum ('quiero_comprarlo', 'consulta');

alter table public.conversations
    add column status         public.conversation_status not null default 'pending',
    add column contact_reason public.contact_reason       not null;

alter table public.reports
    add column conversation_id uuid references public.conversations (id);

create table public.blocked_buyers (
    id          uuid primary key default gen_random_uuid(),
    seller_id   uuid not null references public.profiles (id),
    buyer_id    uuid not null references public.profiles (id),
    report_id   uuid not null references public.reports (id),
    created_at  timestamptz not null default now(),

    -- Un vendedor bloquea a un comprador una sola vez (ver si está bloqueado
    -- es una consulta simple por el par).
    constraint uq_blocked_buyers_seller_buyer unique (seller_id, buyer_id)
);
create index ix_blocked_buyers_seller on public.blocked_buyers (seller_id);

alter table public.blocked_buyers enable row level security;
