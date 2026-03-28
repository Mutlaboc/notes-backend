create table if not exists user_identities (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    provider varchar(32) not null,
    provider_user_id text not null,
    email text,
    email_verified boolean not null default false,
    display_name text,
    avatar_url text,
    provider_username text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    last_login_at timestamptz
);

create unique index if not exists uq_user_identities_provider_subject
    on user_identities(provider, provider_user_id);

create unique index if not exists uq_user_identities_user_provider
    on user_identities(user_id, provider);

create index if not exists idx_user_identities_user_id
    on user_identities(user_id);
