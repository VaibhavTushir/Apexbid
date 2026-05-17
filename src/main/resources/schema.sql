CREATE TABLE IF NOT EXISTS users
(
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50) UNIQUE  NOT NULL,
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash TEXT                NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for fast lookups during auth and recovery
CREATE INDEX IF NOT EXISTS idx_users_username ON users (username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

CREATE TABLE IF NOT EXISTS wallets
(
    user_id      BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    balance      BIGINT NOT NULL DEFAULT 0,
    locked_funds BIGINT NOT NULL DEFAULT 0,
    version      BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS products
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    image_url   TEXT,
    secret_code VARCHAR(500),
    seller_id   BIGINT       NOT NULL REFERENCES users (id),
    buyer_id    BIGINT       REFERENCES users (id) -- null until purchased
);
-- index for rendering a user's inventory tab instantly
CREATE INDEX IF NOT EXISTS idx_products_buyer ON products (buyer_id);
CREATE INDEX IF NOT EXISTS idx_products_seller ON products (seller_id);

CREATE TABLE IF NOT EXISTS auctions
(
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(255) NOT NULL,
    auction_type  VARCHAR(30)  NOT NULL,                                 -- STANDARD, ANTI_SNIPE
    status        VARCHAR(30)  NOT NULL,                                 -- UPCOMING, ACTIVE, PAYMENT_PENDING, SETTLED, CANCELLED
    start_price   BIGINT       NOT NULL,                                 -- Stored as cents
    winning_bid   BIGINT,                                                -- Nullable until a bid is placed
    product_id    BIGINT       NOT NULL UNIQUE REFERENCES products (id), -- UNIQUE enforces the @OneToOne mapping
    seller_id     BIGINT       NOT NULL REFERENCES users (id),
    winner_id     BIGINT REFERENCES users (id),                          -- Nullable until won/active bid
    start_time_ms BIGINT       NOT NULL,
    end_time_ms   BIGINT       NOT NULL
);
-- for redis feeder worker to find upcoming auctions
CREATE INDEX IF NOT EXISTS idx_auctions_upcoming ON auctions (status, start_time_ms);
--for user dashboard
CREATE INDEX IF NOT EXISTS idx_auctions_seller ON auctions (seller_id);
CREATE INDEX IF NOT EXISTS idx_auctions_winner ON auctions (winner_id);



CREATE TABLE IF NOT EXISTS transactions
(
    id               BIGSERIAL PRIMARY KEY,
    sender_id        BIGINT REFERENCES users (id),    -- NULL for DEPOSIT
    receiver_id      BIGINT REFERENCES users (id),    -- NULL for WITHDRAWAL
    auction_id       BIGINT REFERENCES auctions (id), -- NULL for DEPOSIT/WITHDRAWAL
    amount           BIGINT      NOT NULL,            -- Stored as absolute cents
    transaction_type VARCHAR(30) NOT NULL,            -- DEPOSIT, WITHDRAWAL, INITIAL_CHARGE, FINAL_SETTLEMENT
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- Indexes for transaction history lookups
CREATE INDEX IF NOT EXISTS idx_transactions_sender ON transactions (sender_id);
CREATE INDEX IF NOT EXISTS idx_transactions_receiver ON transactions (receiver_id);