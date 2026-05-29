-- Create tables for SwiftPay application

CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(255) NOT NULL UNIQUE,
    sender_id VARCHAR(255) NOT NULL,
    receiver_id VARCHAR(255) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(50) NOT NULL,
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transaction_id ON payments(transaction_id);
CREATE INDEX idx_sender_id ON payments(sender_id);
CREATE INDEX idx_receiver_id ON payments(receiver_id);
CREATE INDEX idx_status ON payments(status);

CREATE TABLE IF NOT EXISTS ledger (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    payment_id BIGINT NOT NULL,
    transaction_id VARCHAR(255) NOT NULL,
    type VARCHAR(10) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance_after NUMERIC(19,2) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_id ON ledger(payment_id);
CREATE INDEX idx_user_id ON ledger(user_id);
CREATE INDEX idx_ledger_timestamp ON ledger(created_at);
CREATE INDEX idx_ledger_user_created_at_desc ON ledger(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS user_balance (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE,
    balance NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_balance ON user_balance(user_id);

-- Insert sample users for testing
INSERT INTO user_balance (user_id, balance, currency, created_at, updated_at) VALUES
    ('user1', 50000.00, 'USD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('user2', 30000.00, 'USD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('user3', 25000.00, 'USD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('user4', 40000.00, 'USD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('user5', 35000.00, 'USD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (user_id) DO NOTHING;

-- Create database user if it doesn't exist (optional for additional security)
-- ALTER USER postgres WITH PASSWORD 'password';

