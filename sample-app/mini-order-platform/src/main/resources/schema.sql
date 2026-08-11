CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    member_id VARCHAR(50) NOT NULL,
    amount_won BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL
);

CREATE TABLE payment_history (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    method VARCHAR(20) NOT NULL,
    amount_won BIGINT NOT NULL,
    success BOOLEAN NOT NULL,
    transaction_id VARCHAR(100)
);

CREATE TABLE order_outbox_events (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload VARCHAR(500) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price_won BIGINT NOT NULL,
    stock INT NOT NULL
);

CREATE TABLE order_line_items (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price_won BIGINT NOT NULL
);
