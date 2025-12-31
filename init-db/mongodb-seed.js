// MongoDB Initialization Script for Monitoring Service
// This script populates the metric_data collection with sample metrics

// Switch to monitoring database
db = db.getSiblingDB('monitoringdb');

// Helper function to generate timestamps
function getTimestamp(hoursAgo) {
    const date = new Date();
    date.setHours(date.getHours() - hoursAgo);
    return date;
}

// Helper function to generate random value within range
function randomInRange(min, max) {
    return Math.round((Math.random() * (max - min) + min) * 100) / 100;
}

// Clear existing data (for clean re-runs)
db.metric_data.deleteMany({});

print('Generating seed data for metric_data collection...');

// Generate metrics for the last 24 hours for various stations
const stations = [
    { id: 1, name: 'BS-SHANGHAI-001' },
    { id: 2, name: 'BS-SHANGHAI-002' },
    { id: 6, name: 'BS-BEIJING-001' },
    { id: 7, name: 'BS-BEIJING-002' },
    { id: 10, name: 'BS-SHENZHEN-001' },
    { id: 11, name: 'BS-SHENZHEN-002' },
    { id: 14, name: 'BS-GUANGZHOU-001' },
    { id: 17, name: 'BS-HANGZHOU-001' },
];

const metrics = [];

stations.forEach(station => {
    // Generate metrics for last 24 hours (one per hour)
    for (let hour = 0; hour < 24; hour++) {
        const timestamp = getTimestamp(hour);

        // CPU Usage (40-95%)
        metrics.push({
            stationId: NumberLong(station.id),
            stationName: station.name,
            metricType: 'CPU_USAGE',
            value: randomInRange(40, 95),
            unit: '%',
            timestamp: timestamp,
            status: 'NORMAL'
        });

        // Memory Usage (50-90%)
        metrics.push({
            stationId: NumberLong(station.id),
            stationName: station.name,
            metricType: 'MEMORY_USAGE',
            value: randomInRange(50, 90),
            unit: '%',
            timestamp: timestamp,
            status: 'NORMAL'
        });

        // Signal Strength (-80 to -40 dBm)
        metrics.push({
            stationId: NumberLong(station.id),
            stationName: station.name,
            metricType: 'SIGNAL_STRENGTH',
            value: randomInRange(-80, -40),
            unit: 'dBm',
            timestamp: timestamp,
            status: 'NORMAL'
        });

        // Power Consumption (1000-3000 W)
        metrics.push({
            stationId: NumberLong(station.id),
            stationName: station.name,
            metricType: 'POWER_CONSUMPTION',
            value: randomInRange(1000, 3000),
            unit: 'W',
            timestamp: timestamp,
            status: 'NORMAL'
        });

        // Temperature (20-70°C)
        metrics.push({
            stationId: NumberLong(station.id),
            stationName: station.name,
            metricType: 'TEMPERATURE',
            value: randomInRange(20, 70),
            unit: '°C',
            timestamp: timestamp,
            status: 'NORMAL'
        });

        // Connection Count (100-5000)
        metrics.push({
            stationId: NumberLong(station.id),
            stationName: station.name,
            metricType: 'CONNECTION_COUNT',
            value: Math.floor(randomInRange(100, 5000)),
            unit: 'connections',
            timestamp: timestamp,
            status: 'NORMAL'
        });

        // Data Throughput (100-1000 Mbps)
        metrics.push({
            stationId: NumberLong(station.id),
            stationName: station.name,
            metricType: 'DATA_THROUGHPUT',
            value: randomInRange(100, 1000),
            unit: 'Mbps',
            timestamp: timestamp,
            status: 'NORMAL'
        });
    }
});

// Add some problematic metrics for stations with issues
// Station 3 (OFFLINE)
for (let hour = 0; hour < 5; hour++) {
    metrics.push({
        stationId: NumberLong(3),
        stationName: 'BS-BEIJING-003',
        metricType: 'CPU_USAGE',
        value: 0,
        unit: '%',
        timestamp: getTimestamp(hour),
        status: 'OFFLINE'
    });
}

// Station 12 (ERROR state) - high error rate
for (let hour = 0; hour < 8; hour++) {
    metrics.push({
        stationId: NumberLong(12),
        stationName: 'BS-SHENZHEN-003',
        metricType: 'CPU_USAGE',
        value: randomInRange(95, 100),
        unit: '%',
        timestamp: getTimestamp(hour),
        status: 'ERROR'
    });
    metrics.push({
        stationId: NumberLong(12),
        stationName: 'BS-SHENZHEN-003',
        metricType: 'TEMPERATURE',
        value: randomInRange(80, 95),
        unit: '°C',
        timestamp: getTimestamp(hour),
        status: 'ERROR'
    });
}

// Insert all metrics
db.metric_data.insertMany(metrics);

print(`Inserted ${metrics.length} metric records`);

// Create indexes
db.metric_data.createIndex({ stationId: 1, timestamp: -1 }, { name: 'idx_station_timestamp' });
db.metric_data.createIndex({ stationId: 1, metricType: 1 }, { name: 'idx_station_type' });
db.metric_data.createIndex({ timestamp: -1 }, { name: 'idx_timestamp' });

print('Indexes created successfully');

// Verify data
print('\n=== Data Summary ===');
print('Total metrics: ' + db.metric_data.countDocuments());
print('\nMetrics by type:');
db.metric_data.aggregate([
    { $group: { _id: '$metricType', count: { $sum: 1 } } },
    { $sort: { _id: 1 } }
]).forEach(printjson);

print('\nMetrics by station:');
db.metric_data.aggregate([
    { $group: { _id: '$stationName', count: { $sum: 1 } } },
    { $sort: { _id: 1 } }
]).forEach(printjson);

print('\nSeed data initialization complete!');
