-- PhoneBeacon backend schema. Run this in Supabase SQL Editor.
-- No GPS/location tables or permissions are used by PhoneBeacon.
create extension if not exists pgcrypto with schema extensions;

create table if not exists public.phonebeacon_pairings (
  id uuid primary key default gen_random_uuid(),
  controller_secret_hash text not null,
  companion_secret_hash text,
  created_at timestamptz not null default now(),
  active boolean not null default true
);

create table if not exists public.phonebeacon_pair_codes (
  code_hash text primary key,
  pairing_id uuid not null references public.phonebeacon_pairings(id) on delete cascade,
  expires_at timestamptz not null,
  claimed_at timestamptz
);

create table if not exists public.phonebeacon_commands (
  id bigint generated always as identity primary key,
  pairing_id uuid not null references public.phonebeacon_pairings(id) on delete cascade,
  command text not null check (command in ('CHIME','FLASH','WHITE','STOP')),
  created_at timestamptz not null default now(),
  delivered_at timestamptz
);

alter table public.phonebeacon_pairings enable row level security;
alter table public.phonebeacon_pair_codes enable row level security;
alter table public.phonebeacon_commands enable row level security;
revoke all on public.phonebeacon_pairings, public.phonebeacon_pair_codes, public.phonebeacon_commands from anon, authenticated;

create or replace function public.phonebeacon_create_pair_code(p_controller_secret text)
returns text language plpgsql security definer set search_path = public, extensions as $$
declare
  v_pairing_id uuid;
  v_code text;
begin
  if length(coalesce(p_controller_secret,'')) < 32 then raise exception 'Invalid controller secret'; end if;
  insert into phonebeacon_pairings(controller_secret_hash)
  values (encode(extensions.digest(p_controller_secret, 'sha256'),'hex'))
  returning id into v_pairing_id;
  loop
    v_code := lpad((floor(random()*1000000))::int::text, 6, '0');
    exit when not exists (
      select 1 from phonebeacon_pair_codes
      where code_hash = encode(extensions.digest(v_code, 'sha256'),'hex')
        and expires_at > now() and claimed_at is null
    );
  end loop;
  insert into phonebeacon_pair_codes(code_hash, pairing_id, expires_at)
  values (encode(extensions.digest(v_code, 'sha256'),'hex'), v_pairing_id, now() + interval '10 minutes');
  return v_code;
end; $$;

create or replace function public.phonebeacon_claim_pair_code(p_code text, p_companion_secret text)
returns boolean language plpgsql security definer set search_path = public, extensions as $$
declare v_pairing_id uuid;
begin
  if length(coalesce(p_companion_secret,'')) < 32 then return false; end if;
  update phonebeacon_pair_codes
     set claimed_at = now()
   where code_hash = encode(extensions.digest(coalesce(p_code,''), 'sha256'),'hex')
     and expires_at > now() and claimed_at is null
  returning pairing_id into v_pairing_id;
  if v_pairing_id is null then return false; end if;
  update phonebeacon_pairings
     set companion_secret_hash = encode(extensions.digest(p_companion_secret,'sha256'),'hex')
   where id = v_pairing_id and active = true and companion_secret_hash is null;
  return found;
end; $$;

create or replace function public.phonebeacon_send_command(p_controller_secret text, p_command text)
returns boolean language plpgsql security definer set search_path = public, extensions as $$
declare v_pairing_id uuid;
begin
  if p_command not in ('CHIME','FLASH','WHITE','STOP') then return false; end if;
  select id into v_pairing_id from phonebeacon_pairings
   where controller_secret_hash = encode(extensions.digest(coalesce(p_controller_secret,''),'sha256'),'hex')
     and active = true and companion_secret_hash is not null
   order by created_at desc
   limit 1;
  if v_pairing_id is null then return false; end if;
  insert into phonebeacon_commands(pairing_id, command) values (v_pairing_id, p_command);
  return true;
end; $$;

create or replace function public.phonebeacon_take_commands(p_companion_secret text)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
declare v_pairing_id uuid; v_result jsonb;
begin
  select id into v_pairing_id from phonebeacon_pairings
   where companion_secret_hash = encode(extensions.digest(coalesce(p_companion_secret,''),'sha256'),'hex')
     and active = true limit 1;
  if v_pairing_id is null then return '[]'::jsonb; end if;
  with pending as (
    select id, command from phonebeacon_commands
    where pairing_id = v_pairing_id and delivered_at is null
    order by id asc limit 20 for update skip locked
  ), marked as (
    update phonebeacon_commands c set delivered_at = now()
    from pending p where c.id = p.id
    returning c.id, c.command
  )
  select coalesce(jsonb_agg(jsonb_build_object('id',id,'command',command) order by id),'[]'::jsonb)
    into v_result from marked;
  return v_result;
end; $$;

revoke all on function public.phonebeacon_create_pair_code(text) from public;
revoke all on function public.phonebeacon_claim_pair_code(text,text) from public;
revoke all on function public.phonebeacon_send_command(text,text) from public;
revoke all on function public.phonebeacon_take_commands(text) from public;
grant execute on function public.phonebeacon_create_pair_code(text) to anon, authenticated;
grant execute on function public.phonebeacon_claim_pair_code(text,text) to anon, authenticated;
grant execute on function public.phonebeacon_send_command(text,text) to anon, authenticated;
grant execute on function public.phonebeacon_take_commands(text) to anon, authenticated;
