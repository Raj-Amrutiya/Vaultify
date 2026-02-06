--Initialization script for Vaultify Database inside of Docker
--This script is auto-executed by PostgreSQL Docker container on first startup

-- 1. Create the database
CREATE DATABASE vaultify;

-- Schema used for Vaultify Database
--Running this script in a DBMS will setup the necessary tables and relationships. 
-- Note: Database should be created separately if not using Docker.

CREATE TABLE users (    id SERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    public_key TEXT NOT NULL,
    private_key_encrypted TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE credentials (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    filename TEXT,
    filepath TEXT UNIQUE NOT NULL,
    metadata TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tokens (
    id SERIAL PRIMARY KEY,
    credential_id INT REFERENCES credentials(id) ON DELETE CASCADE,
    token TEXT UNIQUE NOT NULL,
    expiry TIMESTAMP NOT NULL
);