-- =============================================================================
-- Solo Cuerdas — Moderacion de listing_media
--
-- Agrega el estado de moderacion a cada archivo de media. Para el MVP de
-- tesis, la revision real todavia no existe (el backend aprueba siempre) --
-- pero el campo ya queda listo para cuando haya una revision automatica de
-- verdad (ver docs/ARCHITECTURE.md seccion 5, "Moderacion de media").
-- =============================================================================

create type public.media_moderation_status as enum ('pending', 'approved', 'rejected');

alter table public.listing_media
    add column moderation_status public.media_moderation_status not null default 'pending';

comment on column public.listing_media.moderation_status is
    'pending hasta que se confirma la subida; approved la hace visible en las publicaciones. El MVP de tesis aprueba siempre (sin revision real todavia).';
