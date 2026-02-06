#!/usr/bin/env python3
"""
SON Recommendation Scheduler

Periodically fetches metrics from monitoring service, analyzes them using SON functions,
and posts recommendations back to the monitoring service.
"""

import logging
import threading
import time
import requests
from datetime import datetime, timedelta
from typing import Optional, Dict, Any, List
from son_functions import analyze_cells, SONFunctionType

logger = logging.getLogger(__name__)


class SONScheduler:
    """
    Scheduler that periodically analyzes metrics and generates SON recommendations.
    """

    def __init__(
        self,
        monitoring_service_url: str,
        auth_user: str,
        auth_password: str,
        interval_seconds: int = 300,  # 5 minutes
    ):
        """
        Initialize SON scheduler.

        Args:
            monitoring_service_url: Base URL of monitoring service (e.g., http://monitoring-service:8082)
            auth_user: Username for authentication
            auth_password: Password for authentication
            interval_seconds: How often to run analysis (default: 300s = 5 minutes)
        """
        self.monitoring_url = monitoring_service_url.rstrip('/')
        # Derive API gateway URL from monitoring URL for authenticated requests
        self.api_gateway_url = self.monitoring_url.replace(
            'monitoring-service:8082', 'api-gateway:8080'
        )
        self.auth_user = auth_user
        self.auth_password = auth_password
        self.interval = interval_seconds

        self._running = False
        self._thread: Optional[threading.Thread] = None
        self._session = requests.Session()
        self._token: Optional[str] = None
        self._token_expiry: Optional[datetime] = None

        logger.info(f"SON Scheduler initialized (interval: {interval_seconds}s)")
        logger.info(f"API Gateway URL: {self.api_gateway_url}")

    def _authenticate(self) -> bool:
        """Authenticate with the platform and get JWT token."""
        try:
            # Get token from auth service via API gateway
            auth_url = f"{self.api_gateway_url}/api/v1/auth/login"
            
            response = self._session.post(
                auth_url,
                json={
                    "username": self.auth_user,
                    "password": self.auth_password
                },
                timeout=10
            )
            
            if response.status_code == 200:
                data = response.json()
                self._token = data.get('token')
                # Assume token valid for 1 hour
                self._token_expiry = datetime.now() + timedelta(hours=1)
                logger.info("Successfully authenticated with platform")
                return True
            else:
                logger.error(f"Authentication failed: {response.status_code}")
                return False
                
        except Exception as e:
            logger.error(f"Authentication error: {e}")
            return False

    def _ensure_authenticated(self) -> bool:
        """Ensure we have a valid authentication token."""
        if self._token and self._token_expiry and datetime.now() < self._token_expiry:
            return True
        return self._authenticate()

    def _get_headers(self) -> Dict[str, str]:
        """Get HTTP headers with authentication."""
        headers = {
            'Content-Type': 'application/json',
        }
        if self._token:
            headers['Authorization'] = f'Bearer {self._token}'
        return headers

    def _fetch_metrics(self) -> List[Dict[str, Any]]:
        """
        Fetch recent metrics from monitoring service.
        
        Returns:
            List of metric data dictionaries
        """
        try:
            if not self._ensure_authenticated():
                logger.warning("Cannot fetch metrics - authentication failed")
                return []
            
            # Fetch metrics from last 15 minutes via API gateway
            end_time = datetime.now()
            start_time = end_time - timedelta(minutes=15)

            url = f"{self.api_gateway_url}/api/v1/metrics"
            params = {
                'startTime': start_time.isoformat(),
                'endTime': end_time.isoformat(),
                'limit': 1000
            }
            
            response = self._session.get(
                url,
                params=params,
                headers=self._get_headers(),
                timeout=30
            )
            
            if response.status_code == 200:
                metrics = response.json()
                logger.info(f"Fetched {len(metrics)} metrics from monitoring service")
                return metrics
            else:
                logger.error(f"Failed to fetch metrics: {response.status_code}")
                return []
                
        except Exception as e:
            logger.error(f"Error fetching metrics: {e}")
            return []

    def _convert_metrics_to_cell_data(self, metrics: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """
        Convert monitoring service metrics to cell data format for SON analysis.
        
        Groups metrics by station and creates synthetic cell data.
        """
        # Group metrics by station
        station_metrics: Dict[str, List[Dict]] = {}
        for metric in metrics:
            station_id = str(metric.get('stationId', 'unknown'))
            if station_id not in station_metrics:
                station_metrics[station_id] = []
            station_metrics[station_id].append(metric)
        
        cell_data = []
        
        for station_id, metrics_list in station_metrics.items():
            # Create a synthetic cell for each station
            # In a real system, you'd have actual cell IDs
            cell_id = f"cell-{station_id}-sector-1"
            
            # Aggregate metrics for this station
            latest_metrics = {}
            for metric in metrics_list:
                metric_type = metric.get('metricType', '')
                value = metric.get('value', 0)
                latest_metrics[metric_type] = value
            
            # Map to SON cell data format
            cell = {
                "cell_id": cell_id,
                "station_id": station_id,
                "timestamp": datetime.now().isoformat(),
                # PRB utilization from CPU or synthetic
                "prb_utilization": latest_metrics.get('cpu_usage', 50.0),
                "active_users": int(latest_metrics.get('active_users', 10)),
                "dl_throughput": latest_metrics.get('dl_throughput_mbps', 100.0),
                "ul_throughput": latest_metrics.get('ul_throughput_mbps', 50.0),
                "rsrp_avg": latest_metrics.get('rsrp_dbm', -85.0),
                "sinr_avg": latest_metrics.get('sinr_db', 15.0),
                "handover_success_rate": 100.0 - latest_metrics.get('handover_failure_rate', 1.0),
                "handover_failure_rate": latest_metrics.get('handover_failure_rate', 1.0),
                "rrc_setup_success_rate": latest_metrics.get('rrc_setup_success_rate', 99.5),
                "paging_success_rate": 99.0,
                "interference_level": latest_metrics.get('interference_dbm', -100.0),
                "cqi_avg": latest_metrics.get('cqi', 10.0),
                "power_consumption": latest_metrics.get('power_consumption_watts', 500.0),
                "neighbor_cells": [],  # Would need neighbor relation data
            }
            
            cell_data.append(cell)
        
        logger.info(f"Converted {len(metrics)} metrics into {len(cell_data)} cell data points")
        return cell_data

    def _post_recommendation(self, recommendation: Dict[str, Any]) -> bool:
        """
        Post a SON recommendation to the monitoring service.
        
        Args:
            recommendation: SON recommendation dictionary
            
        Returns:
            True if successful, False otherwise
        """
        try:
            if not self._ensure_authenticated():
                logger.warning("Cannot post recommendation - authentication failed")
                return False

            url = f"{self.api_gateway_url}/api/v1/son"
            
            # Map AI diagnostic format to monitoring service format
            payload = {
                "stationId": int(recommendation.get("station_id", 1)),
                "functionType": recommendation.get("function_type", "MLB").upper(),
                "actionType": recommendation.get("parameters", {}).get("action", "unknown"),
                "actionValue": str(recommendation.get("parameters", {})),
                "description": recommendation.get("description", ""),
                "confidence": 0.85,  # Default confidence
                "expectedImprovement": recommendation.get("expected_impact", {}).get("load_reduction", 0) / 100.0,
                "autoExecutable": False,  # Require manual approval
                "rollbackAction": str(recommendation.get("rollback_params", {})) if recommendation.get("rollback_params") else None,
            }
            
            response = self._session.post(
                url,
                json=payload,
                headers=self._get_headers(),
                timeout=10
            )
            
            if response.status_code in [200, 201]:
                logger.info(f"Posted recommendation {recommendation.get('recommendation_id')} successfully")
                return True
            else:
                logger.error(f"Failed to post recommendation: {response.status_code} - {response.text}")
                return False
                
        except Exception as e:
            logger.error(f"Error posting recommendation: {e}")
            return False

    def _run_analysis(self):
        """Run a single SON analysis cycle."""
        try:
            logger.info("Starting SON analysis cycle")
            
            # 1. Fetch metrics
            metrics = self._fetch_metrics()
            if not metrics:
                logger.warning("No metrics available for analysis")
                return
            
            # 2. Convert to cell data format
            cell_data = self._convert_metrics_to_cell_data(metrics)
            if not cell_data:
                logger.warning("No cell data generated from metrics")
                return
            
            # 3. Run SON analysis
            functions = ["mlb", "mro", "cco", "es"]
            recommendations = analyze_cells(cell_data, functions)
            
            logger.info(f"Generated {len(recommendations)} SON recommendations")
            
            # 4. Post recommendations to monitoring service
            posted_count = 0
            for rec in recommendations:
                if self._post_recommendation(rec):
                    posted_count += 1
                time.sleep(0.5)  # Small delay between posts
            
            logger.info(f"Posted {posted_count}/{len(recommendations)} recommendations successfully")
            
        except Exception as e:
            logger.error(f"Error in SON analysis cycle: {e}", exc_info=True)

    def _scheduler_loop(self):
        """Main scheduler loop."""
        logger.info("SON scheduler loop started")
        
        while self._running:
            try:
                self._run_analysis()
            except Exception as e:
                logger.error(f"Unexpected error in scheduler loop: {e}", exc_info=True)
            
            # Sleep for the interval
            time.sleep(self.interval)
        
        logger.info("SON scheduler loop stopped")

    def start(self):
        """Start the scheduler."""
        if self._running:
            logger.warning("Scheduler already running")
            return
        
        self._running = True
        self._thread = threading.Thread(target=self._scheduler_loop, daemon=True)
        self._thread.start()
        
        logger.info("SON scheduler started")

    def stop(self):
        """Stop the scheduler."""
        if not self._running:
            return
        
        self._running = False
        if self._thread:
            self._thread.join(timeout=10)
        
        logger.info("SON scheduler stopped")


# Singleton instance
_son_scheduler: Optional[SONScheduler] = None


def get_son_scheduler(
    monitoring_url: str = "http://monitoring-service:8082",
    auth_user: str = "admin",
    auth_password: str = "AdminPass12345!",
    interval_seconds: int = 300,
) -> SONScheduler:
    """Get or create the SON scheduler singleton."""
    global _son_scheduler
    if _son_scheduler is None:
        _son_scheduler = SONScheduler(
            monitoring_url,
            auth_user,
            auth_password,
            interval_seconds
        )
    return _son_scheduler
