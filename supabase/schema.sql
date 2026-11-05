create table if not exists accounts (
  id text primary key,
  name text not null,
  email text not null unique,
  salt text not null,
  password_hash text not null,
  birthday text not null default ''
);

alter table accounts add column if not exists birthday text not null default '';

create table if not exists sessions (
  token text primary key,
  account_id text not null references accounts (id) on delete cascade
);

create table if not exists collections (
  account_id text not null references accounts (id) on delete cascade,
  id text not null,
  name text not null,
  position integer not null,
  primary key (account_id, id)
);

create table if not exists places (
  account_id text not null,
  collection_id text not null,
  rest_id text not null,
  name text not null,
  cuisine text not null default '',
  address text not null default '',
  latitude double precision,
  longitude double precision,
  rating double precision not null default 0,
  price_range text not null default '',
  status text not null default 'discovered',
  tags jsonb not null default '[]'::jsonb,
  position integer not null,
  primary key (account_id, collection_id, rest_id),
  foreign key (account_id, collection_id) references collections (account_id, id) on delete cascade
);

create table if not exists reviews (
  id text primary key,
  account_id text not null references accounts (id) on delete cascade,
  rest_id text not null,
  restaurant_name text not null,
  address text not null default '',
  latitude double precision,
  longitude double precision,
  rating integer not null,
  body text not null default '',
  tags jsonb not null default '[]'::jsonb,
  photo text not null default '',
  created_at timestamptz not null default now()
);

alter table accounts enable row level security;
alter table sessions enable row level security;
alter table collections enable row level security;
alter table places enable row level security;
alter table reviews enable row level security;
