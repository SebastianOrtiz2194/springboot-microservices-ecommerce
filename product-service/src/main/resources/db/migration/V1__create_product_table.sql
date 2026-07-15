CREATE TABLE product (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    price NUMERIC(38, 2) NOT NULL,
    stock_quantity INTEGER,
    image_url VARCHAR(500)
);
