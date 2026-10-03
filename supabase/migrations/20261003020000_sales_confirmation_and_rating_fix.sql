-- =============================================================================
-- Solo Cuerdas — Venta con confirmacion por codigo + reputacion anti-colusion
--
-- 1. Agrega sales.confirmation_code: el vendedor genera un codigo de 6
--    digitos al crear la venta; el comprador lo escribe para confirmarla.
--    Evita que una sola parte fabrique una venta sin que la otra participe.
--    Se borra (vuelve a null) una vez confirmada.
-- 2. Redefine recalculate_rating(): la reputacion solo cuenta la PRIMERA
--    review de cada reviewer hacia un mismo reviewee. Evita que dos cuentas
--    (o una misma persona con dos cuentas) se infle la reputacion mutua
--    repitiendo operaciones entre si.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Codigo de confirmacion de venta
-- -----------------------------------------------------------------------------
alter table public.sales
    add column confirmation_code text;

comment on column public.sales.confirmation_code is
    'Codigo de 6 digitos generado por el vendedor al crear la venta; el comprador lo escribe para confirmarla. Null antes de crear o despues de confirmar.';


-- -----------------------------------------------------------------------------
-- 2. Reputacion: solo la primera review por par (reviewer, reviewee)
-- -----------------------------------------------------------------------------
create or replace function public.recalculate_rating()
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
      from (
            select round(avg(first_review.rating)::numeric, 2) as average,
                   count(*)::integer                           as total
              from (
                    select distinct on (rv.reviewer_id) rv.rating
                      from public.reviews rv
                     where rv.reviewee_id = v_profile
                     order by rv.reviewer_id, rv.created_at asc
                   ) first_review
           ) r
     where pr.id = v_profile;

    return null;
end;
$$;
