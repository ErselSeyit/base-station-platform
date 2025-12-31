#!/usr/bin/env python3
"""
Generates 30 days of realistic metrics data for testing trend charts.
Run this after seed_data.py to populate historical data.
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

def get_stations():
    all_stations = []
    statuses = ["ACTIVE", "INACTIVE", "MAINTENANCE", "OFFLINE", "ERROR"]
    
    for status in statuses:
        try:
            url = f"{BASE_URL}/stations?status={status}"
            body, code = make_request(url)
            if code == 200:
                data = json.loads(body)
                if isinstance(data, list):
                    all_stations.extend(data)
                elif isinstance(data, dict) and 'data' in data:
                    all_stations.extend(data['data'])
        except:
            pass
    
    return all_stations

def create_metric(station_id, station_name, metric_type, value, unit, timestamp):
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

def generate_realistic_value(metric_type, hour, day_of_week, base_value, station_factor):
    """
    Generates values that follow realistic patterns - higher during business hours,
    lower on weekends, with some random variation for realism.
    """
    hour_factor = 1.0
    if 8 <= hour <= 18:
        hour_factor = 1.2 + 0.1 * math.sin((hour - 8) * math.pi / 10)
    elif 0 <= hour <= 6:
        hour_factor = 0.7
    else:
        hour_factor = 0.9
    
    day_factor = 1.1 if day_of_week < 5 else 0.85
    noise = random.uniform(0.9, 1.1)
    
    if metric_type == "CPU_USAGE":
        value = base_value * hour_factor * day_factor * noise * station_factor
        return min(max(value, 10), 95)
    
    elif metric_type == "MEMORY_USAGE":
        value = base_value * (0.9 + 0.2 * hour_factor) * noise * station_factor
        return min(max(value, 20), 92)
    
    elif metric_type == "TEMPERATURE":
        base_temp = 35 + (base_value - 40) * 0.3
        time_factor = 0.95 + 0.1 * hour_factor
        value = base_temp * time_factor * noise
        return min(max(value, 25), 75)
    
    elif metric_type == "POWER_CONSUMPTION":
        power_base = 1000 + base_value * 50
        value = power_base * hour_factor * day_factor * noise * station_factor
        return min(max(value, 500), 8000)
    
    elif metric_type == "SIGNAL_STRENGTH":
        value = -65 + random.uniform(-15, 10) * noise
        return min(max(value, -100), -40)
    
    elif metric_type == "UPTIME":
        if random.random() < 0.02:
            value = random.uniform(85, 98)
        else:
            value = random.uniform(99, 100)
        return round(value, 2)
    
    elif metric_type == "CONNECTION_COUNT":
        base_connections = 50 + base_value * 2
        value = base_connections * hour_factor * day_factor * noise * station_factor
        return int(min(max(value, 5), 500))
    
    elif metric_type == "DATA_THROUGHPUT":
        base_throughput = 500 + base_value * 15
        value = base_throughput * hour_factor * day_factor * noise * station_factor
        return min(max(value, 100), 5000)
    
    return base_value

def seed_historical_metrics(days=30, samples_per_day=24):
    print(f"\n{BLUE}📊 Generating {days} days of historical metrics data...{NC}\n")
    
    stations = get_stations()
    if not stations:
        print_error("No stations found! Please run seed_data.py first.")
        return False
    
    print_info(f"Found {len(stations)} stations")
    
    # Calculate time range
    end_time = datetime.now()
    start_time = end_time - timedelta(days=days)
    time_delta = timedelta(hours=24 // samples_per_day)
    
    print_info(f"Time range: {start_time.strftime('%Y-%m-%d')} to {end_time.strftime('%Y-%m-%d')}")
    print_info(f"Samples per day: {samples_per_day}")
    print()
    
    # Metric configurations
    metrics_config = [
        ("CPU_USAGE", "%", 45),
        ("MEMORY_USAGE", "%", 55),
        ("TEMPERATURE", "°C", 45),
        ("POWER_CONSUMPTION", "W", 2500),
        ("SIGNAL_STRENGTH", "dBm", -65),
        ("UPTIME", "%", 99.5),
        ("CONNECTION_COUNT", "connections", 100),
        ("DATA_THROUGHPUT", "Mbps", 1500),
    ]
    
    total_metrics = 0
    total_expected = len(stations) * len(metrics_config) * days * samples_per_day
    
    print_info(f"Expected total metrics: ~{total_expected:,}")
    print()
    
    batch_size = 100
    batch_count = 0
    
    for i, station in enumerate(stations):
        station_id = station.get('id')
        station_name = station.get('stationName', 'Unknown')
        
        if not station_id:
            continue
        
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
                    batch_count += 1
                    
                    # Print progress every batch_size metrics
                    if batch_count >= batch_size:
                        print(f"      Progress: {total_metrics:,} metrics created...")
                        batch_count = 0
                
                time.sleep(0.005)
            
            current_time += time_delta
        
        print_success(f"{station_name}: {station_metrics} metrics")
    
    print(f"\n{GREEN}✓ Successfully created {total_metrics:,} historical metrics!{NC}\n")
    return True

def main():
    print(f"\n{BLUE}{'='*60}{NC}")
    print(f"{BLUE}  Base Station Platform - Historical Metrics Generator{NC}")
    print(f"{BLUE}{'='*60}{NC}\n")
    
    print("Checking API connectivity...")
    body, code = make_request(f"{BASE_URL}/stations?status=ACTIVE")
    if code == 0:
        print_error("Cannot connect to API Gateway at http://localhost:8080")
        print_info("Make sure Docker containers are running: docker-compose up -d")
        return
    
    print_success("API Gateway is accessible\n")
    
    DAYS = 30
    SAMPLES_PER_DAY = 6
    
    print(f"Configuration:")
    print(f"  • Days of history: {DAYS}")
    print(f"  • Samples per day: {SAMPLES_PER_DAY}")
    print(f"  • Interval: Every {24 // SAMPLES_PER_DAY} hour(s)")
    print()
    
    seed_historical_metrics(days=DAYS, samples_per_day=SAMPLES_PER_DAY)
    
    print(f"{BLUE}{'='*60}{NC}")
    print(f"{GREEN}  Done! View trends at http://localhost:3000{NC}")
    print(f"{BLUE}{'='*60}{NC}\n")

if __name__ == "__main__":
    main()
