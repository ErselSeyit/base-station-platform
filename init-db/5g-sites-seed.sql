-- 5G Site Verification and RF Measurement Seed Data
-- Synthetic data based on Huawei SSV structure

-- First ensure we have some 5G base stations
INSERT INTO base_stations (station_name, location, latitude, longitude, station_type, status, power_consumption, description, created_at, updated_at)
VALUES
    ('5G-SITE-001', 'Istanbul, Kadikoy', 40.9928, 29.0230, 'MACRO', 'ACTIVE', 4500.0, '5G NR3500+NR700 Site', NOW(), NOW()),
    ('5G-SITE-002', 'Istanbul, Besiktas', 41.0430, 29.0070, 'MACRO', 'ACTIVE', 4200.0, '5G NR3500+NR700 Site', NOW(), NOW()),
    ('5G-SITE-003', 'Istanbul, Sisli', 41.0600, 28.9870, 'MACRO', 'ACTIVE', 4300.0, '5G NR3500+NR700 Site', NOW(), NOW()),
    ('5G-SITE-004', 'Ankara, Cankaya', 39.9200, 32.8540, 'MACRO', 'ACTIVE', 4100.0, '5G NR3500+NR700 Site', NOW(), NOW()),
    ('5G-SITE-005', 'Ankara, Kecioren', 39.9690, 32.8640, 'MACRO', 'MAINTENANCE', 4400.0, '5G NR3500+NR700 Site - TX Imbalance Issue', NOW(), NOW()),
    ('5G-SITE-006', 'Izmir, Konak', 38.4192, 27.1287, 'MACRO', 'ACTIVE', 4600.0, '5G NR3500+NR700 Site', NOW(), NOW()),
    ('5G-SITE-007', 'Izmir, Bornova', 38.4697, 27.2192, 'MACRO', 'ACTIVE', 4350.0, '5G NR3500+NR700 Site', NOW(), NOW()),
    ('5G-SITE-008', 'Antalya, Muratpasa', 36.8969, 30.7133, 'MACRO', 'ACTIVE', 4250.0, '5G NR3500+NR700 Site', NOW(), NOW()),
    ('5G-SITE-009', 'Bursa, Osmangazi', 40.1826, 29.0665, 'MACRO', 'INACTIVE', 0.0, '5G Site - Pending Activation', NOW(), NOW()),
    ('5G-SITE-010', 'Adana, Seyhan', 36.9914, 35.3308, 'MACRO', 'ACTIVE', 4500.0, '5G NR3500+NR700 Site', NOW(), NOW())
ON CONFLICT (station_name) DO NOTHING;

-- Site Verification Records
INSERT INTO site_verifications (site_id, oss_site_id, du_name, oss, region, city, svt_ready_date, subcontractor, config_status, tn_check_status, site_visit_status, site_visit_date, svt_done_status, svt_done_date, svt_issue, issue_detail, fixed, on_air_status, created_at, updated_at)
VALUES
    ('5G-SITE-001', '5G-SITE-001', '5GSITE001_5GEXP_IST', 'RONE', 'ISTANBUL', 'Kadikoy', '2025-10-15', 'CENTRO', 'OK', 'OK', 'OK', '2025-11-01', 'OK', '2025-11-01', 'NONE', NULL, NULL, 'ON_AIR', NOW(), NOW()),
    ('5G-SITE-002', '5G-SITE-002', '5GSITE002_5GEXP_IST', 'RONE', 'ISTANBUL', 'Besiktas', '2025-10-20', 'CENTRO', 'OK', 'OK', 'OK', '2025-11-05', 'OK', '2025-11-05', 'NONE', NULL, NULL, 'ON_AIR', NOW(), NOW()),
    ('5G-SITE-003', '5G-SITE-003', '5GSITE003_5GEXP_IST', 'RONE', 'ISTANBUL', 'Sisli', '2025-10-25', 'NOYA', 'OK', 'OK', 'OK', '2025-11-10', 'OK', '2025-11-10', 'NONE', NULL, NULL, 'ON_AIR', NOW(), NOW()),
    ('5G-SITE-004', '5G-SITE-004', '5GSITE004_5GEXP_ANK', 'RONE', 'ANKARA', 'Cankaya', '2025-11-01', 'TNI', 'OK', 'OK', 'OK', '2025-11-15', 'OK', '2025-11-15', 'NONE', NULL, NULL, 'ON_AIR', NOW(), NOW()),
    ('5G-SITE-005', '5G-SITE-005', '5GSITE005_5GEXP_ANK', 'RONE', 'ANKARA', 'Kecioren', '2025-11-05', 'TNI', 'OK', 'OK', 'OK', '2025-11-20', 'IN_PROGRESS', NULL, 'RF_ISSUE', 'TX imbalance exceeds 4dB threshold in all sectors', FALSE, 'PENDING', NOW(), NOW()),
    ('5G-SITE-006', '5G-SITE-006', '5GSITE006_5GEXP_IZM', 'RTWO', 'IZMIR', 'Konak', '2025-11-10', 'QUALA', 'OK', 'OK', 'OK', '2025-11-25', 'OK', '2025-11-25', 'NONE', NULL, NULL, 'ON_AIR', NOW(), NOW()),
    ('5G-SITE-007', '5G-SITE-007', '5GSITE007_5GEXP_IZM', 'RTWO', 'IZMIR', 'Bornova', '2025-11-15', 'QUALA', 'OK', 'OK', 'OK', '2025-11-30', 'OK', '2025-11-30', 'NONE', NULL, NULL, 'ON_AIR', NOW(), NOW()),
    ('5G-SITE-008', '5G-SITE-008', '5GSITE008_5GEXP_ANT', 'RTWO', 'ANTALYA', 'Muratpasa', '2025-11-20', 'NOYA', 'OK', 'OK', 'OK', '2025-12-05', 'OK', '2025-12-05', 'NONE', NULL, NULL, 'ON_AIR', NOW(), NOW()),
    ('5G-SITE-009', '5G-SITE-009', '5GSITE009_5GEXP_BRS', 'RONE', 'BURSA', 'Osmangazi', '2025-12-01', 'CENTRO', 'OK', 'NOK', 'PENDING', NULL, 'PENDING', NULL, 'TN', 'TN configuration pending', FALSE, 'BLOCKED', NOW(), NOW()),
    ('5G-SITE-010', '5G-SITE-010', '5GSITE010_5GEXP_ADA', 'RTWO', 'ADANA', 'Seyhan', '2025-11-25', 'TNI', 'OK', 'OK', 'OK', '2025-12-10', 'OK', '2025-12-10', 'NONE', NULL, NULL, 'ON_AIR', NOW(), NOW())
ON CONFLICT (site_id) DO NOTHING;

-- RF Measurements for active sites (3 sectors each, NR3500 100MHz)
-- Using realistic values based on SSV analysis

-- Site 001 - Good performance
INSERT INTO rf_measurements (station_id, sector_number, frequency_band, bandwidth, dl_throughput, ul_throughput, pdcp_throughput, rlc_throughput, rsrp, sinr, latency, rank_indicator, avg_mcs, rb_per_slot, initial_bler, grant, tx_imbalance, pci, cross_connection_check, antenna_direction_check, handover_success_rate, measurement_time)
SELECT id, 1, 'NR3500_N78', 100, 1358.22, 87.85, 1670.89, 1520.90, -78.6, 25.47, 12.84, 'RANK4', 26.19, 252, 6.30, 1503.25, 2.1, 1001, 'PASS', 'PASS', 100.0, NOW()
FROM base_stations WHERE station_name = '5G-SITE-001';

INSERT INTO rf_measurements (station_id, sector_number, frequency_band, bandwidth, dl_throughput, ul_throughput, pdcp_throughput, rlc_throughput, rsrp, sinr, latency, rank_indicator, avg_mcs, rb_per_slot, initial_bler, grant, tx_imbalance, pci, cross_connection_check, antenna_direction_check, handover_success_rate, measurement_time)
SELECT id, 2, 'NR3500_N78', 100, 1206.93, 109.72, 1552.63, 1420.92, -74.55, 31.23, 12.39, 'RANK4', 23.29, 248, 8.78, 1559.29, 1.8, 1002, 'PASS', 'PASS', 100.0, NOW()
FROM base_stations WHERE station_name = '5G-SITE-001';

INSERT INTO rf_measurements (station_id, sector_number, frequency_band, bandwidth, dl_throughput, ul_throughput, pdcp_throughput, rlc_throughput, rsrp, sinr, latency, rank_indicator, avg_mcs, rb_per_slot, initial_bler, grant, tx_imbalance, pci, cross_connection_check, antenna_direction_check, handover_success_rate, measurement_time)
SELECT id, 3, 'NR3500_N78', 100, 1324.31, 111.92, 1649.52, 1435.63, -71.29, 21.54, 12.77, 'RANK4', 22.53, 250, 7.95, 1484.22, 2.3, 1003, 'PASS', 'PASS', 100.0, NOW()
FROM base_stations WHERE station_name = '5G-SITE-001';

-- Site 005 - TX Imbalance issue (failing threshold)
INSERT INTO rf_measurements (station_id, sector_number, frequency_band, bandwidth, dl_throughput, ul_throughput, pdcp_throughput, rlc_throughput, rsrp, sinr, latency, rank_indicator, avg_mcs, rb_per_slot, initial_bler, grant, tx_imbalance, pci, cross_connection_check, antenna_direction_check, handover_success_rate, measurement_time, comments)
SELECT id, 1, 'NR3500_N78', 100, 1250.50, 85.20, 1580.00, 1380.00, -80.2, 22.10, 13.50, 'RANK4', 24.50, 245, 7.20, 1480.00, 5.6, 5001, 'PASS', 'PASS', 98.0, NOW(), 'TX imbalance exceeds 4dB threshold'
FROM base_stations WHERE station_name = '5G-SITE-005';

INSERT INTO rf_measurements (station_id, sector_number, frequency_band, bandwidth, dl_throughput, ul_throughput, pdcp_throughput, rlc_throughput, rsrp, sinr, latency, rank_indicator, avg_mcs, rb_per_slot, initial_bler, grant, tx_imbalance, pci, cross_connection_check, antenna_direction_check, handover_success_rate, measurement_time, comments)
SELECT id, 2, 'NR3500_N78', 100, 1180.30, 95.40, 1490.00, 1320.00, -76.8, 28.50, 12.80, 'RANK4', 22.80, 240, 9.10, 1520.00, 16.7, 5002, 'PASS', 'PASS', 97.0, NOW(), 'CRITICAL: TX imbalance 16.7dB - requires immediate attention'
FROM base_stations WHERE station_name = '5G-SITE-005';

INSERT INTO rf_measurements (station_id, sector_number, frequency_band, bandwidth, dl_throughput, ul_throughput, pdcp_throughput, rlc_throughput, rsrp, sinr, latency, rank_indicator, avg_mcs, rb_per_slot, initial_bler, grant, tx_imbalance, pci, cross_connection_check, antenna_direction_check, handover_success_rate, measurement_time, comments)
SELECT id, 3, 'NR3500_N78', 100, 1290.80, 102.30, 1610.00, 1410.00, -73.5, 19.80, 13.20, 'RANK4', 21.90, 248, 8.50, 1465.00, 5.5, 5003, 'PASS', 'PASS', 99.0, NOW(), 'TX imbalance exceeds 4dB threshold'
FROM base_stations WHERE station_name = '5G-SITE-005';

-- NR700 measurements for Site 001 (10MHz bandwidth)
INSERT INTO rf_measurements (station_id, sector_number, frequency_band, bandwidth, dl_throughput, ul_throughput, rsrp, sinr, latency, rank_indicator, pci, cross_connection_check, antenna_direction_check, handover_success_rate, measurement_time)
SELECT id, 1, 'NR700_N28', 10, 83.20, 28.96, -55.83, 22.14, 14.20, 'RANK2', 1011, 'PASS', 'PASS', 100.0, NOW()
FROM base_stations WHERE station_name = '5G-SITE-001';

INSERT INTO rf_measurements (station_id, sector_number, frequency_band, bandwidth, dl_throughput, ul_throughput, rsrp, sinr, latency, rank_indicator, pci, cross_connection_check, antenna_direction_check, handover_success_rate, measurement_time)
SELECT id, 2, 'NR700_N28', 10, 90.98, 28.38, -51.30, 19.19, 13.80, 'RANK2', 1012, 'PASS', 'PASS', 100.0, NOW()
FROM base_stations WHERE station_name = '5G-SITE-001';

INSERT INTO rf_measurements (station_id, sector_number, frequency_band, bandwidth, dl_throughput, ul_throughput, rsrp, sinr, latency, rank_indicator, pci, cross_connection_check, antenna_direction_check, handover_success_rate, measurement_time)
SELECT id, 3, 'NR700_N28', 10, 87.64, 29.22, -53.58, 8.78, 14.50, 'RANK2', 1013, 'PASS', 'PASS', 100.0, NOW()
FROM base_stations WHERE station_name = '5G-SITE-001';

-- Add more synthetic measurements for other active sites
INSERT INTO rf_measurements (station_id, sector_number, frequency_band, bandwidth, dl_throughput, ul_throughput, rsrp, sinr, latency, rank_indicator, avg_mcs, rb_per_slot, initial_bler, tx_imbalance, pci, cross_connection_check, antenna_direction_check, handover_success_rate, measurement_time)
SELECT id, s.sector, 'NR3500_N78', 100,
    1200 + RANDOM() * 200,  -- DL throughput 1200-1400 Mbps
    80 + RANDOM() * 40,     -- UL throughput 80-120 Mbps
    -85 + RANDOM() * 20,    -- RSRP -85 to -65 dBm
    15 + RANDOM() * 20,     -- SINR 15-35 dB
    10 + RANDOM() * 5,      -- Latency 10-15 ms
    'RANK4',
    20 + RANDOM() * 8,      -- MCS 20-28
    240 + FLOOR(RANDOM() * 20)::int,  -- RB 240-260
    5 + RANDOM() * 5,       -- BLER 5-10%
    1 + RANDOM() * 3,       -- TX imbalance 1-4 dB (passing)
    s.sector * 1000 + id::int,
    'PASS', 'PASS', 100.0, NOW()
FROM base_stations, generate_series(1, 3) AS s(sector)
WHERE station_name IN ('5G-SITE-002', '5G-SITE-003', '5G-SITE-004', '5G-SITE-006', '5G-SITE-007', '5G-SITE-008', '5G-SITE-010');

SELECT 'Synthetic 5G data seeded successfully' AS result;
