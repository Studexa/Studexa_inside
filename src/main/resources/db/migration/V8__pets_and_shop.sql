-- Step 11: Study Buddy (pets) and shop.

-- Pets table: one pet per student.
create table pets (
    user_id uuid primary key references users (id) on delete cascade,
    species varchar(20) not null check (species in ('owl', 'cat', 'fox', 'elephant')),
    name varchar(50) not null default 'Buddy',
    stage varchar(20) not null default 'baby' check (stage in ('baby', 'young', 'grown', 'legend')),
    mood varchar(20) not null default 'happy' check (mood in ('happy', 'sleepy', 'sparkling')),
    accessories jsonb not null default '[]'::jsonb,
    equipped_accessory varchar(20),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Shop items available for purchase with coins.
create table shop_items (
    id varchar(40) primary key,
    name_en varchar(100) not null,
    name_km varchar(100) not null,
    kind varchar(20) not null check (kind in ('accessory', 'theme', 'freeze')),
    price integer not null check (price >= 0),
    accessory_type varchar(20),
    is_active boolean not null default true
);

-- Seed shop items.
insert into shop_items (id, name_en, name_km, kind, price, accessory_type, is_active) values
('hat', 'Hat', 'មុខលិប', 'accessory', 60, 'hat', true),
('glasses', 'Glasses', 'ឈាំងថ្នាំ', 'accessory', 80, 'glasses', true),
('scarf', 'Scarf', 'ក្រមាស់', 'accessory', 70, 'scarf', true),
('cape', 'Cape', 'ស្នេហារាយ', 'accessory', 150, 'cape', true),
('freeze', 'Streak Freeze', 'ការប្រើប្រាស់ដែលមានគ្រប់គ្រាន់', 'freeze', 50, null, true),
('theme_ocean', 'Ocean Theme', 'បណ្តាញទឹក', 'theme', 120, null, true);

-- User-owned shop items (purchased accessories, themes, freezes).
create table user_shop_items (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users (id) on delete cascade,
    shop_item_id varchar(40) not null references shop_items (id),
    purchased_at timestamptz not null default now(),
    unique (user_id, shop_item_id)
);

create index idx_user_shop_items_user on user_shop_items (user_id);
