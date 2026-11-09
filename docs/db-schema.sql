-- =====================================================================================
-- FileShareX — PostgreSQL schema (MongoDB → PostgreSQL migration)
--
-- Database-per-service: 4 databases, 4 containers (postgres:17-alpine)
--   postgres-users          host 5433 -> 5432   db: filesharex_users
--   postgres-files          host 5434 -> 5432   db: filesharex_files
--   postgres-payments       host 5435 -> 5432   db: filesharex_payments
--   postgres-notifications  host 5436 -> 5432   db: filesharex_notifications
--   role: filesharex / ${POSTGRES_PASSWORD} (default: filesharex)
--
-- Source of truth: the JPA entities. With spring.jpa.hibernate.ddl-auto=update
-- Hibernate creates these tables automatically at first startup — this file documents
-- the resulting schema and can be used for manual provisioning / review.
-- Capture the authoritative DDL after first boot with: pg_dump --schema-only <db>
--
-- Naming: Hibernate 6 CamelCaseToUnderscoresNamingStrategy (clerkId -> clerk_id).
-- No foreign keys: services only reference each other logically via clerk_id.
-- =====================================================================================


-- =====================================================================================
-- DATABASE: filesharex_users  (user-service)
-- Entities: filesharex.user.document.ProfileDocument, filesharex.user.document.UserCredits
-- =====================================================================================

CREATE DATABASE filesharex_users;
\connect filesharex_users

CREATE TABLE profiles (
    id         varchar(36) PRIMARY KEY,          -- @Id @GeneratedValue(strategy = GenerationType.UUID)
    clerk_id   varchar(255),                     -- Clerk user id (logical reference, no FK)
    email      varchar(255) UNIQUE,              -- @Column(unique = true) -> SQLState 23505 -> HTTP 409
    first_name varchar(255),
    last_name  varchar(255),
    credits    integer,                          -- seeded with 5 on user.created webhook
    photo_url  varchar(255),
    created_at timestamp with time zone          -- java.time.Instant
);

CREATE TABLE user_credits (
    id       varchar(36) PRIMARY KEY,
    clerk_id varchar(255),
    credits  integer,
    plan     varchar(255)                        -- BASIC | PREMIUM | ULTIMATE
);


-- =====================================================================================
-- DATABASE: filesharex_files  (file-service)
-- Entity: filesharex.file.document.FileMetadataDocument
-- =====================================================================================

CREATE DATABASE filesharex_files;
\connect filesharex_files

CREATE TABLE files (
    id            varchar(36) PRIMARY KEY,
    name          varchar(255),                  -- original filename
    type          varchar(255),                  -- MIME type
    size          bigint,                        -- bytes
    clerk_id      varchar(255),
    is_public     boolean,                       -- public share toggle
    file_location varchar(255),                  -- R2 / local storage key
    uploaded_at   timestamp                      -- java.time.LocalDateTime
);


-- =====================================================================================
-- DATABASE: filesharex_payments  (payment-service)
-- Entity: filesharex.payment.document.PaymentTransaction
-- =====================================================================================

CREATE DATABASE filesharex_payments;
\connect filesharex_payments

CREATE TABLE payment_transactions (
    id               varchar(36) PRIMARY KEY,
    clerk_id         varchar(255),
    order_id         varchar(255),               -- Razorpay order id
    payment_id       varchar(255),               -- Razorpay payment id (NULL while PENDING)
    plan_id          varchar(255),               -- premium | ultimate
    amount           integer,                    -- minor units as sent by the client
    currency         varchar(255),
    credits_added    integer,
    status           varchar(255),               -- PENDING | SUCCESS | FAILED
    transaction_date timestamp,
    user_email       varchar(255),               -- snapshot from user-service at order time
    user_name        varchar(255)                -- snapshot from user-service at order time
);


-- =====================================================================================
-- DATABASE: filesharex_notifications  (notification-service)
-- Entity: filesharex.notification.model.Notification
-- =====================================================================================

CREATE DATABASE filesharex_notifications;
\connect filesharex_notifications

CREATE TABLE notifications (
    id             varchar(36) PRIMARY KEY,
    clerk_id       varchar(255),
    type           varchar(255),                 -- CREDIT_LOW | PAYMENT_SUCCESS | FILE_UPLOADED | SYSTEM
    title          varchar(255),
    message        varchar(255),
    read           boolean,                      -- "read" is a NON-reserved keyword in PostgreSQL
    reference_id   varchar(255),                 -- payment order id / file id / NULL
    reference_type varchar(255),                 -- PAYMENT | FILE | SYSTEM
    created_at     timestamp
);


-- =====================================================================================
-- RECOMMENDED INDEXES (NOT created by Hibernate — every clerk_id lookup is otherwise
-- a sequential scan). Apply manually per database after first boot.
-- =====================================================================================

-- filesharex_users
CREATE INDEX idx_profiles_clerk_id     ON profiles (clerk_id);
CREATE INDEX idx_user_credits_clerk_id ON user_credits (clerk_id);

-- filesharex_files
CREATE INDEX idx_files_clerk_id        ON files (clerk_id);

-- filesharex_payments
CREATE INDEX idx_paytx_clerk_id        ON payment_transactions (clerk_id);
CREATE INDEX idx_paytx_order_id        ON payment_transactions (order_id);   -- findByOrderId()
CREATE INDEX idx_paytx_clerk_date_desc ON payment_transactions (clerk_id, transaction_date DESC);

-- filesharex_notifications
CREATE INDEX idx_notif_clerk_date_desc ON notifications (clerk_id, created_at DESC);
CREATE INDEX idx_notif_clerk_unread    ON notifications (clerk_id) WHERE read = false;


-- =====================================================================================
-- MIGRATION NOTES (see docs/ERRORS-AND-FIXES.md, section 14)
--   * IDs are UUID strings, not 24-hex Mongo ObjectIds.
--   * Mongo collections file_metadata / transactions map to tables files /
--     payment_transactions; camelCase document fields become snake_case columns.
--   * No data migration was performed — MongoDB documents stay in the old mongo_*
--     volumes (export steps in docs/ERRORS-AND-FIXES.md, issue 14.10).
--   * Only constraint: profiles.email UNIQUE. Recommended for production:
--     spring.jpa.hibernate.ddl-auto: validate + Flyway/Liquibase instead of update.
-- =====================================================================================
