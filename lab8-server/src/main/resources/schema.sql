CREATE SEQUENCE IF NOT EXISTS product_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS coord_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS owner_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS loc_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS users
(
    login
    VARCHAR
(
    255
) PRIMARY KEY,
    password_hash VARCHAR
(
    40
) NOT NULL
    );

CREATE TABLE IF NOT EXISTS locations
(
    id
    BIGINT
    PRIMARY
    KEY
    DEFAULT
    nextval
(
    'loc_id_seq'
),
    x REAL NOT NULL,
    y INTEGER NOT NULL,
    z BIGINT NOT NULL,
    name VARCHAR
(
    986
) NOT NULL
    );

CREATE TABLE IF NOT EXISTS coordinates
(
    id
    BIGINT
    PRIMARY
    KEY
    DEFAULT
    nextval
(
    'coord_id_seq'
),
    x INTEGER NOT NULL,
    y REAL NOT NULL
    );

CREATE TABLE IF NOT EXISTS owners
(
    id
    BIGINT
    PRIMARY
    KEY
    DEFAULT
    nextval
(
    'owner_id_seq'
),
    name VARCHAR
(
    255
) NOT NULL,
    birthday TIMESTAMPTZ,
    passport_id VARCHAR
(
    255
),
    location_id BIGINT NOT NULL REFERENCES locations
(
    id
)
    );

CREATE TABLE IF NOT EXISTS products
(
    id
    INTEGER
    PRIMARY
    KEY
    DEFAULT
    nextval
(
    'product_id_seq'
),
    name VARCHAR
(
    255
) NOT NULL,
    coordinate_id BIGINT NOT NULL REFERENCES coordinates
(
    id
),
    creation_date TIMESTAMPTZ NOT NULL,
    price REAL,
    part_number VARCHAR
(
    83
) NOT NULL UNIQUE,
    unit_of_measure VARCHAR
(
    20
) NOT NULL,
    owner_id BIGINT NOT NULL REFERENCES owners
(
    id
),
    created_by VARCHAR
(
    255
) NOT NULL REFERENCES users
(
    login
)
    );