-- ============================================
-- Auth Service - User Initialization
-- ============================================
-- This script creates initial users for demo/development.
-- Automatically executed when database container starts.
--
-- Default credentials:
--   admin/admin (ROLE_ADMIN)
--   user/user   (ROLE_USER)
--
-- PRODUCTION WARNING:
-- In production, DO NOT use this script. Instead:
-- 1. Remove this file from docker-compose.yml volumes
-- 2. Create users through a secure admin interface
-- 3. Use strong, unique passwords
-- 4. Enable 2FA/MFA
-- ============================================

-- Create users table if it doesn't exist
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create admin user
-- Password: "admin" (BCrypt hash with strength 10)
INSERT INTO users (username, password_hash, role, enabled, created_at, updated_at)
VALUES (
    'admin',
    '$2a$10$MdnzbYWc5/PVAWi4EBQNm.IHRyPvbDYC8A970.6Jf/yRoq7SQJXpG',
    'ROLE_ADMIN',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (username) DO NOTHING;

-- Create regular user
-- Password: "user" (BCrypt hash with strength 10)
INSERT INTO users (username, password_hash, role, enabled, created_at, updated_at)
VALUES (
    'user',
    '$2a$10$2tqN0AqG7/szcAjnzQNxoOeqrVtp/eiVJiaW5TdDA62b3BHE91j7S',
    'ROLE_USER',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (username) DO NOTHING;
