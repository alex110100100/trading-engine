-- Baseline schema (matches JPA entities; Spring Boot default physical naming: camelCase -> snake_case).

CREATE TABLE trades (
    id               BIGSERIAL PRIMARY KEY,
    buyer_order_id   VARCHAR(255) NOT NULL,
    seller_order_id  VARCHAR(255) NOT NULL,
    symbol           VARCHAR(255) NOT NULL,
    price            NUMERIC(19, 8) NOT NULL,
    quantity         NUMERIC(19, 8) NOT NULL,
    executed_at      TIMESTAMPTZ NOT NULL
);

CREATE TABLE open_orders (
    order_id             VARCHAR(128) PRIMARY KEY,
    symbol               VARCHAR(32) NOT NULL,
    order_side           VARCHAR(8) NOT NULL,
    price                NUMERIC(19, 8) NOT NULL,
    remaining_quantity   NUMERIC(19, 8) NOT NULL,
    original_quantity    NUMERIC(19, 8) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL
);

CREATE TABLE order_states (
    order_id VARCHAR(128) PRIMARY KEY,
    symbol   VARCHAR(32) NOT NULL,
    status   VARCHAR(32) NOT NULL
);
