-- ============================================================================
-- Base Station Platform - Unified PostgreSQL Seed Script
-- ============================================================================
-- This script initializes ALL PostgreSQL databases for the platform:
--   1. Auth Service (authdb) - Users and authentication
--   2. Base Station Service (basestationdb) - Base station inventory
--   3. Notification Service (notificationdb) - Alerts and notifications
--
-- Usage for Kubernetes:
--   kubectl exec -i -n basestation-platform deployment/postgres-<service> -- \
--     psql -U postgres -d <dbname> < init-db/postgres-unified-seed.sql
--
-- PRODUCTION WARNING:
-- DO NOT use default credentials (admin/admin, user/user) in production!
-- Change passwords immediately after first deployment.
-- ============================================================================

-- ============================================================================
-- SECTION 1: AUTH SERVICE (authdb)
-- ============================================================================
-- Run this section on postgres-auth deployment with authdb database

\echo '========================================='
\echo 'Initializing Auth Service Database'
\echo '========================================='

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

\echo 'Auth Service: Created users table and seeded default users'
SELECT COUNT(*) as total_users, STRING_AGG(username, ', ') as usernames FROM users;

-- ============================================================================
-- SECTION 2: BASE STATION SERVICE (basestationdb)
-- ============================================================================
-- Run this section on postgres-basestation deployment with basestationdb database

\echo ''
\echo '========================================='
\echo 'Initializing Base Station Service Database'
\echo '========================================='

-- Insert 26 sample base stations across different Chinese cities
INSERT INTO base_stations (station_name, location, latitude, longitude, station_type, status, power_consumption, description, created_at, updated_at) VALUES
-- Shanghai (5 stations)
('BS-SHANGHAI-001', 'Pudong District, Shanghai', 31.2304, 121.4737, 'MACRO_CELL', 'ACTIVE', 2500.50, 'Primary macro cell station serving Pudong financial district', NOW(), NOW()),
('BS-SHANGHAI-002', 'Huangpu District, Shanghai', 31.2165, 121.4365, 'MACRO_CELL', 'ACTIVE', 2450.75, 'Main coverage for Huangpu business area', NOW(), NOW()),
('BS-SHANGHAI-003', 'Xuhui District, Shanghai', 31.1880, 121.4372, 'MICRO_CELL', 'ACTIVE', 1800.25, 'Micro cell for dense residential area', NOW(), NOW()),
('BS-SHANGHAI-004', 'Jingan District, Shanghai', 31.2252, 121.4450, 'SMALL_CELL', 'ACTIVE', 1200.00, 'Small cell deployment for shopping district', NOW(), NOW()),
('BS-SHANGHAI-005', 'Hongkou District, Shanghai', 31.2768, 121.4955, 'PICO_CELL', 'MAINTENANCE', 850.50, 'Pico cell undergoing scheduled maintenance', NOW(), NOW()),

-- Beijing (4 stations)
('BS-BEIJING-001', 'Chaoyang District, Beijing', 39.9289, 116.4324, 'MACRO_CELL', 'ACTIVE', 2600.00, 'Main station covering CBD area', NOW(), NOW()),
('BS-BEIJING-002', 'Haidian District, Beijing', 39.9590, 116.2982, 'MACRO_CELL', 'ACTIVE', 2550.00, 'Coverage for technology hub and universities', NOW(), NOW()),
('BS-BEIJING-003', 'Dongcheng District, Beijing', 39.9289, 116.4163, 'MICRO_CELL', 'OFFLINE', 1750.00, 'Station currently offline - power system failure', NOW(), NOW()),
('BS-BEIJING-004', 'Xicheng District, Beijing', 39.9139, 116.3664, 'SMALL_CELL', 'ACTIVE', 1150.00, 'Small cell for historic district', NOW(), NOW()),

-- Shenzhen (4 stations)
('BS-SHENZHEN-001', 'Futian District, Shenzhen', 22.5431, 114.0579, 'MACRO_CELL', 'ACTIVE', 2700.00, 'Primary coverage for central business district', NOW(), NOW()),
('BS-SHENZHEN-002', 'Nanshan District, Shenzhen', 22.5329, 113.9308, 'MACRO_CELL', 'ACTIVE', 2650.00, 'Tech park and university area coverage', NOW(), NOW()),
('BS-SHENZHEN-003', 'Luohu District, Shenzhen', 22.5561, 114.1318, 'MICRO_CELL', 'ERROR', 1850.00, 'Station reporting errors - requires investigation', NOW(), NOW()),
('BS-SHENZHEN-004', 'Baoan District, Shenzhen', 22.5569, 113.8288, 'FEMTO_CELL', 'ACTIVE', 600.00, 'Indoor femto cell for shopping mall', NOW(), NOW()),

-- Guangzhou (3 stations)
('BS-GUANGZHOU-001', 'Tianhe District, Guangzhou', 23.1291, 113.3223, 'MACRO_CELL', 'ACTIVE', 2580.00, 'Main station for business district', NOW(), NOW()),
('BS-GUANGZHOU-002', 'Yuexiu District, Guangzhou', 23.1288, 113.2644, 'MICRO_CELL', 'ACTIVE', 1780.00, 'City center coverage', NOW(), NOW()),
('BS-GUANGZHOU-003', 'Haizhu District, Guangzhou', 23.0888, 113.3175, 'SMALL_CELL', 'INACTIVE', 1100.00, 'Station temporarily deactivated for upgrades', NOW(), NOW()),

-- Hangzhou (2 stations)
('BS-HANGZHOU-001', 'Xihu District, Hangzhou', 30.2741, 120.1551, 'MACRO_CELL', 'ACTIVE', 2500.00, 'Coverage for West Lake tourist area', NOW(), NOW()),
('BS-HANGZHOU-002', 'Binjiang District, Hangzhou', 30.2085, 120.2118, 'MICRO_CELL', 'ACTIVE', 1820.00, 'High-tech development zone', NOW(), NOW()),

-- Chengdu (2 stations)
('BS-CHENGDU-001', 'Jinjiang District, Chengdu', 30.6598, 104.0803, 'MACRO_CELL', 'ACTIVE', 2520.00, 'City center primary coverage', NOW(), NOW()),
('BS-CHENGDU-002', 'Wuhou District, Chengdu', 30.6417, 104.0430, 'MICRO_CELL', 'ACTIVE', 1790.00, 'Residential and commercial area', NOW(), NOW()),

-- Xi'an (2 stations)
('BS-XIAN-001', 'Yanta District, Xian', 34.2146, 108.9542, 'MACRO_CELL', 'ACTIVE', 2480.00, 'Main coverage for southern district', NOW(), NOW()),
('BS-XIAN-002', 'Beilin District, Xian', 34.2571, 108.9469, 'SMALL_CELL', 'ACTIVE', 1180.00, 'Historic city center coverage', NOW(), NOW()),

-- Wuhan (2 stations)
('BS-WUHAN-001', 'Wuchang District, Wuhan', 30.5467, 114.3163, 'MACRO_CELL', 'ACTIVE', 2550.00, 'East bank coverage', NOW(), NOW()),
('BS-WUHAN-002', 'Hankou District, Wuhan', 30.5964, 114.2989, 'MICRO_CELL', 'MAINTENANCE', 1800.00, 'Scheduled maintenance in progress', NOW(), NOW()),

-- Nanjing (2 stations)
('BS-NANJING-001', 'Xuanwu District, Nanjing', 32.0472, 118.7989, 'MACRO_CELL', 'ACTIVE', 2530.00, 'Central district primary station', NOW(), NOW()),
('BS-NANJING-002', 'Qinhuai District, Nanjing', 32.0103, 118.7820, 'SMALL_CELL', 'ACTIVE', 1160.00, 'Dense urban area coverage', NOW(), NOW())

ON CONFLICT DO NOTHING;

\echo 'Base Station Service: Created base_stations table and seeded 26 stations'
SELECT COUNT(*) as total_stations FROM base_stations;
SELECT status, COUNT(*) as count FROM base_stations GROUP BY status ORDER BY status;
SELECT station_type, COUNT(*) as count FROM base_stations GROUP BY station_type ORDER BY station_type;

-- ============================================================================
-- SECTION 3: NOTIFICATION SERVICE (notificationdb)
-- ============================================================================
-- Run this section on postgres-notification deployment with notificationdb database

\echo ''
\echo '========================================='
\echo 'Initializing Notification Service Database'
\echo '========================================='

-- Note: station_id references base_stations in base-station-service
-- Using station IDs 1-26 which should exist after running Section 2

INSERT INTO notifications (station_id, message, type, status, created_at, sent_at) VALUES
-- Recent alerts (last 24 hours)
(3, 'Station BS-BEIJING-003 has gone offline - power system failure detected', 'ERROR', 'SENT', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours'),
(12, 'Station BS-SHENZHEN-003 reporting high error rate - immediate attention required', 'ERROR', 'SENT', NOW() - INTERVAL '4 hours', NOW() - INTERVAL '4 hours'),
(5, 'Scheduled maintenance started for station BS-SHANGHAI-005', 'MAINTENANCE', 'SENT', NOW() - INTERVAL '6 hours', NOW() - INTERVAL '6 hours'),
(23, 'Station BS-WUHAN-002 maintenance window initiated', 'MAINTENANCE', 'SENT', NOW() - INTERVAL '8 hours', NOW() - INTERVAL '8 hours'),

-- Warnings (last few days)
(1, 'CPU usage exceeded 85% threshold on BS-SHANGHAI-001', 'WARNING', 'SENT', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
(10, 'High temperature alert: BS-SHENZHEN-001 exceeds normal operating range', 'WARNING', 'SENT', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
(6, 'Memory usage approaching capacity on BS-BEIJING-001', 'WARNING', 'SENT', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
(14, 'Power consumption spike detected on BS-GUANGZHOU-001', 'WARNING', 'SENT', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),

-- Info messages
(16, 'Station BS-GUANGZHOU-003 deactivated for planned upgrade - expected downtime 48 hours', 'INFO', 'SENT', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),
(2, 'Firmware update completed successfully on BS-SHANGHAI-002', 'INFO', 'SENT', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),
(7, 'Performance optimization completed on BS-BEIJING-002', 'INFO', 'SENT', NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days'),
(17, 'New configuration deployed to BS-HANGZHOU-001', 'INFO', 'SENT', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),

-- Older alerts (last week)
(11, 'Network congestion detected on BS-SHENZHEN-002 - bandwidth capacity reached', 'ALERT', 'SENT', NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days'),
(4, 'Connection count threshold exceeded on BS-SHANGHAI-004 - 95% capacity', 'ALERT', 'SENT', NOW() - INTERVAL '6 days', NOW() - INTERVAL '6 days'),
(19, 'Signal strength degradation on BS-CHENGDU-001', 'WARNING', 'SENT', NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'),

-- Pending notifications (not yet sent)
(8, 'Scheduled maintenance notification: BS-BEIJING-004 maintenance window planned for next week', 'INFO', 'PENDING', NOW(), NULL),
(13, 'Firmware update available for BS-SHENZHEN-004', 'INFO', 'PENDING', NOW(), NULL),
(20, 'Performance audit scheduled for BS-CHENGDU-002', 'INFO', 'PENDING', NOW(), NULL)

ON CONFLICT DO NOTHING;

\echo 'Notification Service: Created notifications table and seeded 18 notifications'
SELECT COUNT(*) as total_notifications FROM notifications;
SELECT type, COUNT(*) as count FROM notifications GROUP BY type ORDER BY type;
SELECT status, COUNT(*) as count FROM notifications GROUP BY status ORDER BY status;

\echo ''
\echo '========================================='
\echo 'Database Initialization Complete!'
\echo '========================================='
