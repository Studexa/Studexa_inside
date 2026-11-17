-- Step 10: game engine tables (XP, levels, streaks, coins, badges).

-- One row per student. Written only by GameService inside a transaction
-- with PESSIMISTIC_WRITE locking on the game_state row.
create table game_state (
    user_id uuid primary key references users (id) on delete cascade,
    xp_total bigint not null default 0,
    level integer not null default 1,
    xp_in_level bigint not null default 0,
    coins bigint not null default 0,
    streak_current integer not null default 0,
    streak_best integer not null default 0,
    streak_freezes integer not null default 0,
    last_active_date date not null,
    daily_xp_today bigint not null default 0,
    daily_xp_date date not null,
    updated_at timestamptz not null default now(),
    constraint game_state_level_check check (level >= 1),
    constraint game_state_coins_check check (coins >= 0)
);

-- Audit trail of every XP grant. Used to detect double awards and cheating.
create table xp_events (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    source varchar(40) not null,
    amount integer not null,
    reference_id uuid,
    created_at timestamptz not null default now()
);

create index idx_xp_events_user on xp_events (user_id);
create index idx_xp_events_created on xp_events (created_at);

-- Badge definitions (seed data). Coins are awarded the first time a user
-- earns the badge.
create table badges (
    code varchar(40) primary key,
    name_en varchar(100) not null,
    name_km varchar(100) not null,
    description_en text not null,
    description_km text not null,
    coins_reward integer not null default 0
);

-- Badges earned by each student. Unique per (user, badge).
create table user_badges (
    user_id uuid not null references users (id) on delete cascade,
    badge_code varchar(40) not null references badges (code),
    earned_at timestamptz not null default now(),
    primary key (user_id, badge_code)
);

create index idx_user_badges_user on user_badges (user_id);

-- Seed badge data (see Section 7.5 of the build guide).
insert into badges (code, name_en, name_km, description_en, description_km, coins_reward) values
('firstFlip', 'First Flip', 'ការផ្ដល់លើកទូតដូចក្នុងការអានទីមួយ', 'Review your first card.', 'អានលើកទូតដូចក្នុងការអានទីមួយដល់ដំណើរការដែលមានសុវត្ថិភាព.', 10),
('cardCollector', 'Card Collector', 'ការសរុបលើកទូត', 'Create 50 cards.', 'បង្កើត 50 លើកទូត.', 30),
('centuryClub', 'Century Club', 'ការអានលើកទូត 100', 'Review 100 cards in total.', 'អានលើកទូតមួយសប្ដាហ៍ 100 លើកទូតដោយផ្ទុកគ្នាគ្នាដោយជ្រើសរើសអ្នកនិស្សិតមួយនាក់.', 30),
('memoryMaster', 'Memory Master', 'មហាប្រធានប្រអប់រាយ', '100 cards in Review state with 21+ day intervals.', 'លើកទូត 100 ដែលមានអំណរចាស់ 21 ថ្ងៃឬច្រើនជាប់នៅក្នុងស្ថានីស Review.', 100),
('weekWarrior', 'Week Warrior', 'អ្នកប្រុសសប្ដាហ៍', '7-day streak.', 'ការស្រាប់ដំណើរការត្រូវគ្នា 7 ថ្ងៃដែលត្រូវគ្នាក្នុងម៉ោងដែលប្រើប្រាស់របស់អ្នក.', 50),
('monthMonarch', 'Month Monarch', 'រដូវទីក្រុងខែ', '30-day streak.', 'ការស្រាប់ដំណើរការត្រូវគ្នា 30 ថ្ងៃដែលត្រូវគ្នាក្នុងម៉ោងដែលប្រើប្រាស់របស់អ្នក.', 200),
('focusHero', 'Focus Hero', 'អ្នកប្រាស់ការក្រុងចិត្តល្អ', 'Complete 10 focus sessions.', 'បានបញ្ចប់ 10 ប្រអប់ក្រុងចិត្តល្អ.', 40),
('nightOwl', 'Night Owl', 'ស្លឹកចាស់', 'Study before 6:00 or after 22:00 local time (3 times).', 'អានបន្ទាប់ពីម៉ោង 6:00 ឬប៉ាណែលពីម៉ោង 22:00 តែមួយនាក់នៅក្នុងម៉ោងដែលប្រើប្រាស់របស់អ្នកក្នុង 3 ពេល.', 20),
('earlyBird', 'Early Bird', 'ច្រើននាក់ដែលដឹងពីច្រើន', 'Study before 6:00 local time (3 times).', 'អានបន្ទាប់ពីម៉ោង 6:00 តែមួយនាក់នៅក្នុងម៉ោងដែលប្រើប្រាស់របស់អ្នកក្នុង 3 ពេល.', 20),
('perfectRound', 'Perfect Round', 'រឿងសម្រាប់ការសម្លាប់ដោយផ្សព្វផ្សាយដែលមានគ្រប់គ្រាន់', 'Every card Good or Easy in a session of 20+ cards.', 'លើកទូតទាំងអស់នៅក្នុងសម្រាប់អានមួយដែលមានលើកទូត 20 លើកទូតឬច្រើនដែលអាចអានបានល្អឬអាចអានបានល្អ។', 40),
('bossSlayer', 'Boss Slayer', 'អ្នកប្រុសបុសស្ម័គ្រ', 'Win your first boss battle.', 'ជ្រើសរើសបុសស្ម័គ្រក្រោមណាមួយដែលមានគ្រប់គ្រាន់ដល់ដំណើរការដែលមានសុវត្ថិភាពទីមួយ។', 60),
('noteNinja', 'Note Ninja', 'អ្នកសរុបចំណងជើងរបស់អ្នក', 'Create 10 notes.', 'បង្កើតចំណងជើង 10 ចំណងជើងសម្រាប់របស់អ្នក។', 30),
('quizWhiz', 'Quiz Whiz', 'អ្នកប្រុសបញ្ចប់ប្រវត្តិ', '100% on a quiz with 10+ questions.', 'បានស្រឡាញ់ 100% នៅក្នុងប្រវត្តិអានមួយដែលមានសំណួរ 10 ជម្រើសឬច្រើន។', 40),
('sharingStar', 'Sharing Star', 'គ្រប់គ្រាន់របស់អ្នកបង្អួចគ្នា', 'Publish a deck that 5 students copy.', 'លើកទីផ្នូរមួយដែលអាចអានបាននៅក្នុងសាលារៀនច្រើនហើយមានអ្នកចូលច្រែក 5 នាក់ក្នុងលំដាប់របស់អ្នក។', 80),
('comebackKid', 'Comeback Kid', 'អ្នកបង្កើតចូលច្រែកថ្មី', 'Return after 7+ days away and finish a session.', 'រីករាយបន្ទាប់បន្ទាប់ចាប់ផ្តើមការអានបន្ទាប់ក្រោយពីបង្ហាញតាមរយៈក្រោយ 7 ថ្ងៃឬច្រើនក្នុងសម្រាប់អានមួយដល់ដំណើរការ។', 25),
('subjectExplorer', 'Subject Explorer', 'ការស្វែងរកមុខវិជ្ជា', 'Study in 5 different subjects.', 'អានក្រើសរើសមុខវិជ្ជាច្រើនពីមុខវិជ្ជា 5 ចំនួនដែលមិនជាមួយគ្នា។', 30),
('polyglot', 'Polyglot', 'អ្នកនិយាយភាសាច្រើន', 'Use the app in both Khmer and English.', 'ប្រើប្រាស់អាស៊ូប្រព័ន្ធដោយភាសាខ្មែរ និងភាសាអង់គ្លេស។', 10);
