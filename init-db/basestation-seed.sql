-- Base Station Service Seed Data
-- This script populates the base_stations table with sample data for testing/demo

-- Insert sample base stations across different locations
INSERT INTO base_stations (station_name, location, latitude, longitude, station_type, status, power_consumption, description, created_at, updated_at) VALUES
('BS-SHANGHAI-001', 'Pudong District, Shanghai', 31.2304, 121.4737, 'MACRO_CELL', 'ACTIVE', 2500.50, 'Primary macro cell station serving Pudong financial district', NOW(), NOW()),
('BS-SHANGHAI-002', 'Huangpu District, Shanghai', 31.2165, 121.4365, 'MACRO_CELL', 'ACTIVE', 2450.75, 'Main coverage for Huangpu business area', NOW(), NOW()),
('BS-SHANGHAI-003', 'Xuhui District, Shanghai', 31.1880, 121.4372, 'MICRO_CELL', 'ACTIVE', 1800.25, 'Micro cell for dense residential area', NOW(), NOW()),
('BS-SHANGHAI-004', 'Jingan District, Shanghai', 31.2252, 121.4450, 'SMALL_CELL', 'ACTIVE', 1200.00, 'Small cell deployment for shopping district', NOW(), NOW()),
('BS-SHANGHAI-005', 'Hongkou District, Shanghai', 31.2768, 121.4955, 'PICO_CELL', 'MAINTENANCE', 850.50, 'Pico cell undergoing scheduled maintenance', NOW(), NOW()),

('BS-BEIJING-001', 'Chaoyang District, Beijing', 39.9289, 116.4324, 'MACRO_CELL', 'ACTIVE', 2600.00, 'Main station covering CBD area', NOW(), NOW()),
('BS-BEIJING-002', 'Haidian District, Beijing', 39.9590, 116.2982, 'MACRO_CELL', 'ACTIVE', 2550.00, 'Coverage for technology hub and universities', NOW(), NOW()),
('BS-BEIJING-003', 'Dongcheng District, Beijing', 39.9289, 116.4163, 'MICRO_CELL', 'OFFLINE', 1750.00, 'Station currently offline - power system failure', NOW(), NOW()),
('BS-BEIJING-004', 'Xicheng District, Beijing', 39.9139, 116.3664, 'SMALL_CELL', 'ACTIVE', 1150.00, 'Small cell for historic district', NOW(), NOW()),

('BS-SHENZHEN-001', 'Futian District, Shenzhen', 22.5431, 114.0579, 'MACRO_CELL', 'ACTIVE', 2700.00, 'Primary coverage for central business district', NOW(), NOW()),
('BS-SHENZHEN-002', 'Nanshan District, Shenzhen', 22.5329, 113.9308, 'MACRO_CELL', 'ACTIVE', 2650.00, 'Tech park and university area coverage', NOW(), NOW()),
('BS-SHENZHEN-003', 'Luohu District, Shenzhen', 22.5561, 114.1318, 'MICRO_CELL', 'ERROR', 1850.00, 'Station reporting errors - requires investigation', NOW(), NOW()),
('BS-SHENZHEN-004', 'Baoan District, Shenzhen', 22.5569, 113.8288, 'FEMTO_CELL', 'ACTIVE', 600.00, 'Indoor femto cell for shopping mall', NOW(), NOW()),

('BS-GUANGZHOU-001', 'Tianhe District, Guangzhou', 23.1291, 113.3223, 'MACRO_CELL', 'ACTIVE', 2580.00, 'Main station for business district', NOW(), NOW()),
('BS-GUANGZHOU-002', 'Yuexiu District, Guangzhou', 23.1288, 113.2644, 'MICRO_CELL', 'ACTIVE', 1780.00, 'City center coverage', NOW(), NOW()),
('BS-GUANGZHOU-003', 'Haizhu District, Guangzhou', 23.0888, 113.3175, 'SMALL_CELL', 'INACTIVE', 1100.00, 'Station temporarily deactivated for upgrades', NOW(), NOW()),

('BS-HANGZHOU-001', 'Xihu District, Hangzhou', 30.2741, 120.1551, 'MACRO_CELL', 'ACTIVE', 2500.00, 'Coverage for West Lake tourist area', NOW(), NOW()),
('BS-HANGZHOU-002', 'Binjiang District, Hangzhou', 30.2085, 120.2118, 'MICRO_CELL', 'ACTIVE', 1820.00, 'High-tech development zone', NOW(), NOW()),

('BS-CHENGDU-001', 'Jinjiang District, Chengdu', 30.6598, 104.0803, 'MACRO_CELL', 'ACTIVE', 2520.00, 'City center primary coverage', NOW(), NOW()),
('BS-CHENGDU-002', 'Wuhou District, Chengdu', 30.6417, 104.0430, 'MICRO_CELL', 'ACTIVE', 1790.00, 'Residential and commercial area', NOW(), NOW()),

('BS-XIAN-001', 'Yanta District, Xian', 34.2146, 108.9542, 'MACRO_CELL', 'ACTIVE', 2480.00, 'Main coverage for southern district', NOW(), NOW()),
('BS-XIAN-002', 'Beilin District, Xian', 34.2571, 108.9469, 'SMALL_CELL', 'ACTIVE', 1180.00, 'Historic city center coverage', NOW(), NOW()),

('BS-WUHAN-001', 'Wuchang District, Wuhan', 30.5467, 114.3163, 'MACRO_CELL', 'ACTIVE', 2550.00, 'East bank coverage', NOW(), NOW()),
('BS-WUHAN-002', 'Hankou District, Wuhan', 30.5964, 114.2989, 'MICRO_CELL', 'MAINTENANCE', 1800.00, 'Scheduled maintenance in progress', NOW(), NOW()),

('BS-NANJING-001', 'Xuanwu District, Nanjing', 32.0472, 118.7989, 'MACRO_CELL', 'ACTIVE', 2530.00, 'Central district primary station', NOW(), NOW()),
('BS-NANJING-002', 'Qinhuai District, Nanjing', 32.0103, 118.7820, 'SMALL_CELL', 'ACTIVE', 1160.00, 'Dense urban area coverage', NOW(), NOW());

-- Verify insert
SELECT COUNT(*) as total_stations FROM base_stations;
SELECT status, COUNT(*) as count FROM base_stations GROUP BY status ORDER BY status;
SELECT station_type, COUNT(*) as count FROM base_stations GROUP BY station_type ORDER BY station_type;
