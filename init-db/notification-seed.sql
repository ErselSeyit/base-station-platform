-- Notification Service Seed Data
-- This script populates the notifications table with sample alerts and messages

-- Note: station_id references base_stations table in base-station-service
-- We'll use station IDs 1-25 which should exist after running basestation-seed.sql

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
(20, 'Performance audit scheduled for BS-CHENGDU-002', 'INFO', 'PENDING', NOW(), NULL);

-- Verify insert
SELECT COUNT(*) as total_notifications FROM notifications;
SELECT type, COUNT(*) as count FROM notifications GROUP BY type ORDER BY type;
SELECT status, COUNT(*) as count FROM notifications GROUP BY status ORDER BY status;
SELECT type, status, COUNT(*) as count FROM notifications GROUP BY type, status ORDER BY type, status;
