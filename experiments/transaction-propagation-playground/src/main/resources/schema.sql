CREATE TABLE accounts (
    id INT PRIMARY KEY,
    balance INT NOT NULL
);

INSERT INTO accounts (id, balance) VALUES (1, 100);

CREATE TABLE ledger (
    id IDENTITY PRIMARY KEY,
    event VARCHAR(50) NOT NULL
);
