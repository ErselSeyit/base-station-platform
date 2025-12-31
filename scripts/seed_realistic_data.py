#!/usr/bin/env python3
"""
Seeds realistic base station data with 7 days of metrics.
Replaces all existing data with fresh, realistic data.
"""

import json
import urllib.request
import urllib.parse
import random
import math
from datetime import datetime, timedelta
import time

BASE_URL = "http://localhost:8080/api/v1"

GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
BLUE = "\033[94m"
NC = "\033[0m"

def print_success(msg):
    print(f"  {GREEN}✓{NC} {msg}")

def print_error(msg):
    print(f"  {RED}✗{NC} {msg}")

def print_info(msg):
    print(f"  {YELLOW}ℹ{NC} {msg}")

def make_request(url, method="GET", data=None):
    headers = {"Content-Type": "application/json"}

    try:
        if data:
            req = urllib.request.Request(url, data=json.dumps(data).encode('utf-8'), headers=headers, method=method)
        else:
            req = urllib.request.Request(url, headers=headers, method=method)

        with urllib.request.urlopen(req, timeout=10) as response:
            return response.read().decode('utf-8'), response.status
    except urllib.error.HTTPError as e:
        return e.read().decode('utf-8') if e.fp else str(e), e.code
    except Exception as e:
        return str(e), 0

# Realistic base station data - Major US cities
STATIONS = [
    {
        "stationName": "NYC-Manhattan-001",
        "location": "Manhattan, New York, NY",
        "latitude": 40.7580,
        "longitude": -73.9855,
        "stationType": "MACRO_CELL",
        "status": "ACTIVE",
        "powerConsumption": 3500,
        "description": "Times Square macro cell - high traffic area"
    },
    {
        "stationName": "SF-Financial-001",
        "location": "Financial District, San Francisco, CA",
        "latitude": 37.7946,
        "longitude": -122.3999,
        "stationType": "MACRO_CELL",
        "status": "ACTIVE",
        "powerConsumption": 3200,
        "description": "Downtown SF - business district coverage"
    },
    {
        "stationName": "LA-Hollywood-001",
        "location": "Hollywood, Los Angeles, CA",
        "latitude": 34.0928,
        "longitude": -118.3287,
        "stationType": "MACRO_CELL",
        "status": "ACTIVE",
        "powerConsumption": 3800,
        "description": "Hollywood Boulevard macro cell"
    },
    {
        "stationName": "CHI-Loop-001",
        "location": "The Loop, Chicago, IL",
        "latitude": 41.8781,
        "longitude": -87.6298,
        "stationType": "MACRO_CELL",
        "status": "ACTIVE",
        "powerConsumption": 3100,
        "description": "Downtown Chicago business district"
    },
    {
        "stationName": "SEA-Downtown-001",
        "location": "Downtown Seattle, WA",
        "latitude": 47.6062,
        "longitude": -122.3321,
        "stationType": "MICRO_CELL",
        "status": "ACTIVE",
        "powerConsumption": 1500,
        "description": "Pike Place Market area coverage"
    },
    {
        "stationName": "BOS-BackBay-001",
        "location": "Back Bay, Boston, MA",
        "latitude": 42.3467,
        "longitude": -71.0818,
        "stationType": "MICRO_CELL",
        "status": "ACTIVE",
        "powerConsumption": 1400,
        "description": "Boston Back Bay commercial area"
    },
    {
        "stationName": "NYC-Central-Park-002",
        "location": "Central Park, New York, NY",
        "latitude": 40.7829,
        "longitude": -73.9654,
        "stationType": "SMALL_CELL",
        "status": "ACTIVE",
        "powerConsumption": 800,
        "description": "Central Park recreational area coverage"
    },
    {
        "stationName": "MIA-SouthBeach-001",
        "location": "South Beach, Miami, FL",
        "latitude": 25.7907,
        "longitude": -80.1300,
        "stationType": "MACRO_CELL",
        "status": "ACTIVE",
        "powerConsumption": 3600,
        "description": "Miami Beach tourist area - high demand"
    },
    {
        "stationName": "DEN-Airport-001",
        "location": "Denver International Airport, CO",
        "latitude": 39.8561,
        "longitude": -104.6737,
        "stationType": "MACRO_CELL",
        "status": "ACTIVE",
        "powerConsumption": 4200,
        "description": "Airport terminal coverage"
    },
    {
        "stationName": "ATL-Midtown-001",
        "location": "Midtown Atlanta, GA",
        "latitude": 33.7838,
        "longitude": -84.3845,
        "stationType": "MICRO_CELL",
        "status": "MAINTENANCE",
        "powerConsumption": 1600,
        "description": "Midtown business district - scheduled maintenance"
    }
]

def create_station(station_data):
    """Create a base station"""
    body, code = make_request(f"{BASE_URL}/stations", "POST", station_data)
    if code in [200, 201]:
        return json.loads(body)
    else:
        print_error(f"Failed to create station {station_data['stationName']}: {body}")
        return None

def generate_realistic_value(metric_type, hour, day_of_week, base_value, station_factor):
    """
    Generates realistic metric values with time-based patterns.
    Values follow NEW validation ranges.
    """
    # Time-based factors
    hour_factor = 1.0
    if 8 <= hour <= 18:  # Business hours
        hour_factor = 1.2 + 0.1 * math.sin((hour - 8) * math.pi / 10)
    elif 0 <= hour <= 6:  # Night hours
        hour_factor = 0.7
    else:  # Evening
        hour_factor = 0.9

    # Weekend vs weekday
    day_factor = 1.1 if day_of_week < 5 else 0.85

    # Random noise
    noise = random.uniform(0.9, 1.1)

    if metric_type == "CPU_USAGE":
        # Range: 0-100%
        value = base_value * hour_factor * day_factor * noise * station_factor
        return min(max(value, 10), 95)

    elif metric_type == "MEMORY_USAGE":
        # Range: 0-100%
        value = base_value * (0.9 + 0.2 * hour_factor) * noise * station_factor
        return min(max(value, 20), 90)

    elif metric_type == "TEMPERATURE":
        # Range: -50 to 150°C, typical 20-75°C
        base_temp = 35 + (base_value - 40) * 0.3
        time_factor = 0.95 + 0.1 * hour_factor
        value = base_temp * time_factor * noise
        return min(max(value, 25), 75)  # NEW: Max 75°C

    elif metric_type == "POWER_CONSUMPTION":
        # Range: 0-50,000W, typical 500-8000W
        power_base = 1000 + base_value * 50
        value = power_base * hour_factor * day_factor * noise * station_factor
        return min(max(value, 500), 8000)

    elif metric_type == "SIGNAL_STRENGTH":
        # Range: -120 to -20 dBm, typical -100 to -40 dBm
        value = -65 + random.uniform(-15, 10) * noise
        return min(max(value, -100), -40)

    elif metric_type == "UPTIME":
        # Range: 0-100%, target >99%
        if random.random() < 0.02:  # 2% chance of lower uptime
            value = random.uniform(95, 98.5)
        else:
            value = random.uniform(99, 100)
        return round(value, 2)

    elif metric_type == "CONNECTION_COUNT":
        # Range: 0-10,000, typical 5-500
        base_connections = 50 + base_value * 2
        value = base_connections * hour_factor * day_factor * noise * station_factor
        return int(min(max(value, 5), 500))

    elif metric_type == "DATA_THROUGHPUT":
        # Range: 0-100,000 Mbps, typical 100-5000 Mbps
        # NEW: Max 5000 Mbps (was 10,000)
        base_throughput = 500 + base_value * 15
        value = base_throughput * hour_factor * day_factor * noise * station_factor
        return min(max(value, 100), 5000)  # NEW: Max 5000

    return base_value

def create_metric(station_id, station_name, metric_type, value, unit, timestamp):
    """Create a metric data point"""
    data = {
        "stationId": station_id,
        "stationName": station_name,
        "metricType": metric_type,
        "value": round(value, 2),
        "unit": unit,
        "timestamp": timestamp.strftime("%Y-%m-%dT%H:%M:%S")
    }

    body, code = make_request(f"{BASE_URL}/metrics", "POST", data)
    return code in [200, 201]

def seed_metrics(stations, days=7, samples_per_day=6):
    """Generate metrics for all stations over specified days"""
    print(f"\n{BLUE}📊 Generating {days} days of metrics data...{NC}\n")

    # Calculate time range
    end_time = datetime.now()
    start_time = end_time - timedelta(days=days)
    time_delta = timedelta(hours=24 // samples_per_day)

    print_info(f"Time range: {start_time.strftime('%Y-%m-%d')} to {end_time.strftime('%Y-%m-%d')}")
    print_info(f"Samples per day: {samples_per_day} (every {24//samples_per_day} hours)")
    print()

    # Metric configurations with NEW validation ranges
    metrics_config = [
        ("CPU_USAGE", "%", 45),
        ("MEMORY_USAGE", "%", 55),
        ("TEMPERATURE", "°C", 45),
        ("POWER_CONSUMPTION", "W", 2500),
        ("SIGNAL_STRENGTH", "dBm", -65),
        ("UPTIME", "%", 99.5),
        ("CONNECTION_COUNT", "connections", 100),
        ("DATA_THROUGHPUT", "Mbps", 1500),  # Realistic throughput
    ]

    total_metrics = 0
    total_expected = len(stations) * len(metrics_config) * days * samples_per_day

    print_info(f"Expected total metrics: ~{total_expected:,}")
    print()

    for i, station in enumerate(stations):
        station_id = station.get('id')
        station_name = station.get('stationName', 'Unknown')

        # Station-specific variation factor
        station_factor = 0.8 + random.random() * 0.4

        print(f"  [{i+1}/{len(stations)}] Generating metrics for {station_name}...")

        current_time = start_time
        station_metrics = 0

        while current_time <= end_time:
            hour = current_time.hour
            day_of_week = current_time.weekday()

            for metric_type, unit, base_value in metrics_config:
                value = generate_realistic_value(
                    metric_type, hour, day_of_week, base_value, station_factor
                )

                if create_metric(station_id, station_name, metric_type, value, unit, current_time):
                    total_metrics += 1
                    station_metrics += 1

                time.sleep(0.002)  # Faster seeding

            current_time += time_delta

        print_success(f"{station_name}: {station_metrics} metrics created")

    print(f"\n{GREEN}✓ Successfully created {total_metrics:,} metrics!{NC}\n")
    return total_metrics

def main():
    print(f"\n{BLUE}{'='*70}{NC}")
    print(f"{BLUE}  Realistic Base Station Data Seeder - 7 Day Period{NC}")
    print(f"{BLUE}{'='*70}{NC}\n")

    # Check API connectivity
    print("Checking API connectivity...")
    body, code = make_request(f"{BASE_URL}/stations")
    if code == 0:
        print_error("Cannot connect to API Gateway at http://localhost:8080")
        print_info("Make sure Docker containers are running")
        return

    print_success("API Gateway is accessible\n")

    # Step 1: Create stations
    print(f"{BLUE}Step 1: Creating {len(STATIONS)} realistic base stations...{NC}\n")
    created_stations = []

    for station_data in STATIONS:
        station = create_station(station_data)
        if station:
            created_stations.append(station)
            print_success(f"Created: {station['stationName']} ({station['stationType']}) - {station['location']}")
        time.sleep(0.1)

    print(f"\n{GREEN}✓ Created {len(created_stations)} stations{NC}\n")

    if not created_stations:
        print_error("No stations created. Exiting.")
        return

    # Step 2: Generate metrics
    print(f"\n{BLUE}Step 2: Generating metrics for 7 days...{NC}")
    print_info("Sampling: Every 4 hours (6 samples/day)")
    print()

    total = seed_metrics(created_stations, days=7, samples_per_day=6)

    # Summary
    print(f"\n{BLUE}{'='*70}{NC}")
    print(f"{GREEN}  ✓ Data Seeding Complete!{NC}")
    print(f"{BLUE}{'='*70}{NC}")
    print(f"\n  📍 Stations Created: {len(created_stations)}")
    print(f"  📊 Metrics Created: {total:,}")
    print(f"  📅 Time Period: 7 days")
    print(f"  🔄 Update Frequency: Every 4 hours")
    print(f"\n  🌐 View Dashboard: {GREEN}http://localhost:3000{NC}")
    print(f"  📈 View Metrics: {GREEN}http://localhost:3000/metrics{NC}\n")
    print(f"{BLUE}{'='*70}{NC}\n")

if __name__ == "__main__":
    main()
