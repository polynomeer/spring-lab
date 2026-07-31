CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    amount INT NOT NULL
);

CREATE TABLE outbox_events (
    id BIGINT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    payload VARCHAR(500) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE processed_messages (
    message_id BIGINT PRIMARY KEY
);
