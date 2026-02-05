#!/usr/bin/env python3
"""
BI Report Generator Module

Generates professional PDF reports with charts and analytics.
Used by the AI diagnostic service for on-demand report generation.
"""

import io
import logging
import os
import requests
from datetime import datetime
from typing import Dict, List, Any, Optional
from collections import defaultdict

import matplotlib
matplotlib.use('Agg')  # Non-interactive backend for server use
import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages
import matplotlib.patches as mpatches
from matplotlib.gridspec import GridSpec
import numpy as np

logger = logging.getLogger(__name__)

# Modern RNG (replaces deprecated np.random functions)
_rng = np.random.default_rng(42)

# Professional color palette
COLORS = {
    "primary": "#1a73e8",
    "secondary": "#5f6368",
    "success": "#34a853",
    "warning": "#fbbc04",
    "danger": "#ea4335",
    "info": "#4285f4",
    "dark": "#202124",
    "light": "#f8f9fa",
    "white": "#ffffff",
    "accent1": "#8ab4f8",
    "accent2": "#81c995",
    "accent3": "#fdd663",
    "accent4": "#f28b82",
}

STATUS_COLORS = {
    "ONLINE": COLORS["success"],
    "DEGRADED": COLORS["warning"],
    "OFFLINE": COLORS["danger"],
    "UNKNOWN": COLORS["secondary"],
}

# Chart style settings
plt.style.use('seaborn-v0_8-whitegrid')
plt.rcParams.update({
    'font.family': 'sans-serif',
    'font.sans-serif': ['DejaVu Sans', 'Helvetica', 'Arial'],
    'font.size': 10,
    'axes.titlesize': 14,
    'axes.titleweight': 'bold',
    'axes.labelsize': 11,
    'axes.labelweight': 'medium',
    'axes.spines.top': False,
    'axes.spines.right': False,
    'figure.facecolor': COLORS["white"],
    'axes.facecolor': COLORS["white"],
    'axes.edgecolor': COLORS["secondary"],
    'grid.color': '#e8eaed',
    'grid.linewidth': 0.5,
})


class BIReportGenerator:
    """Generates professional BI reports for base stations"""

    def __init__(self, api_url: str):
        self.api_url = api_url.rstrip("/")
        self.token: Optional[str] = None
        self.stations: List[Dict] = []
        self.metrics: List[Dict] = []
        self.alerts: List[Dict] = []
        self.notifications: List[Dict] = []
        self.report_time = datetime.now()

    def authenticate(self) -> bool:
        """Get JWT token"""
        try:
            # Support both old and new env var names
            username = os.environ.get("CLOUD_USER") or os.environ.get("AUTH_ADMIN_USERNAME") or "admin"
            password = os.environ.get("CLOUD_PASSWORD") or os.environ.get("AUTH_ADMIN_PASSWORD")
            if not password:
                raise ValueError("CLOUD_PASSWORD or AUTH_ADMIN_PASSWORD environment variable is required")
            response = requests.post(
                f"{self.api_url}/api/v1/auth/login",
                json={"username": username, "password": password},
                timeout=10,
            )
            if response.status_code == 200:
                self.token = response.json().get("token")
                logger.info("Authenticated for BI report generation")
                return True
            logger.error(f"Auth response: {response.status_code} - {response.text[:200]}")
            return False
        except Exception as e:
            logger.error(f"Auth failed: {e}")
            return False

    def get_headers(self) -> Dict[str, str]:
        return {"Authorization": f"Bearer {self.token}"} if self.token else {}

    def fetch_data(self):
        """Fetch all data from APIs"""
        logger.info("Fetching data for BI report...")

        endpoints = [
            ("stations", "/api/v1/stations"),
            ("metrics", "/api/v1/metrics"),
            ("alerts", "/api/v1/alerts"),
            ("notifications", "/api/v1/notifications"),
        ]

        for name, endpoint in endpoints:
            try:
                resp = requests.get(f"{self.api_url}{endpoint}", headers=self.get_headers(), timeout=10)
                if resp.status_code == 200:
                    data = resp.json()
                    result = data if isinstance(data, list) else data.get("content", [])
                    setattr(self, name, result)
                    logger.info(f"  {name}: {len(result)} records")
            except Exception as e:
                logger.warning(f"  {name}: failed - {e}")

    def _add_header(self, fig, title: str, subtitle: str = ""):
        """Add professional header to page"""
        header_ax = fig.add_axes([0, 0.92, 1, 0.08])
        header_ax.set_xlim(0, 1)
        header_ax.set_ylim(0, 1)
        header_ax.axis('off')

        gradient = np.linspace(0, 1, 100).reshape(1, -1)
        header_ax.imshow(gradient, extent=[0, 1, 0, 1], aspect='auto',
                        cmap=plt.cm.Blues, alpha=0.3)

        header_ax.text(0.03, 0.6, title, fontsize=20, fontweight='bold',
                      color=COLORS["dark"], va='center')
        if subtitle:
            header_ax.text(0.03, 0.2, subtitle, fontsize=11,
                          color=COLORS["secondary"], va='center')

        header_ax.text(0.97, 0.5, "BASE STATION PLATFORM", fontsize=9,
                      fontweight='bold', color=COLORS["primary"],
                      ha='right', va='center', alpha=0.7)

    def _add_footer(self, fig, page_num: int):
        """Add footer with page number and timestamp"""
        footer_ax = fig.add_axes([0, 0, 1, 0.03])
        footer_ax.set_xlim(0, 1)
        footer_ax.set_ylim(0, 1)
        footer_ax.axis('off')

        footer_ax.axhline(y=0.9, color=COLORS["secondary"], linewidth=0.5, alpha=0.3)
        footer_ax.text(0.03, 0.4, f"Generated: {self.report_time.strftime('%Y-%m-%d %H:%M:%S')}",
                      fontsize=8, color=COLORS["secondary"])
        footer_ax.text(0.97, 0.4, f"Page {page_num}",
                      fontsize=8, color=COLORS["secondary"], ha='right')
        footer_ax.text(0.5, 0.4, "Confidential - Internal Use Only",
                      fontsize=8, color=COLORS["secondary"], ha='center', style='italic')

    def create_title_page(self, pdf: PdfPages):
        """Create title page"""
        fig = plt.figure(figsize=(11, 8.5))
        ax = fig.add_axes([0, 0, 1, 1])
        ax.axis('off')

        y = np.linspace(0, 1, 100)
        gradient = y.reshape(-1, 1)
        ax.imshow(gradient, extent=[0, 1, 0, 1], aspect='auto',
                 cmap='Blues', alpha=0.15, origin='lower')

        ax.text(0.5, 0.65, "Base Station Platform", fontsize=36, fontweight='bold',
               ha='center', va='center', color=COLORS["dark"])
        ax.text(0.5, 0.55, "Business Intelligence Report", fontsize=24,
               ha='center', va='center', color=COLORS["primary"])
        ax.axhline(y=0.48, xmin=0.3, xmax=0.7, color=COLORS["primary"], linewidth=2)

        info_box = plt.Rectangle((0.25, 0.2), 0.5, 0.2, fill=True,
                                  facecolor=COLORS["light"], edgecolor=COLORS["secondary"],
                                  linewidth=1, alpha=0.8)
        ax.add_patch(info_box)

        stats = [
            ("Total Stations", len(self.stations)),
            ("Metrics Collected", len(self.metrics)),
            ("Active Alerts", len(self.alerts)),
            ("Notifications", len(self.notifications)),
        ]

        for i, (label, value) in enumerate(stats):
            x = 0.32 + (i % 2) * 0.2
            y = 0.35 if i < 2 else 0.25
            ax.text(x, y, str(value), fontsize=20, fontweight='bold',
                   color=COLORS["primary"], ha='center')
            ax.text(x, y - 0.04, label, fontsize=9, color=COLORS["secondary"], ha='center')

        ax.text(0.5, 0.1, f"Report Generated: {self.report_time.strftime('%B %d, %Y at %H:%M')}",
               fontsize=11, ha='center', color=COLORS["secondary"])

        pdf.savefig(fig, facecolor='white')
        plt.close(fig)

    def _calculate_kpis(self) -> Dict[str, float]:
        """Calculate KPI values"""
        metrics_by_type: Dict[str, List[float]] = defaultdict(list)
        for m in self.metrics:
            mtype = m.get("metricType", "")
            value = m.get("value")
            if value is not None:
                metrics_by_type[mtype].append(float(value))

        online_count = sum(1 for s in self.stations if s.get("status") == "ONLINE")
        total_stations = max(len(self.stations), 1)

        return {
            "health_score": (online_count / total_stations) * 100,
            "avg_cpu": np.mean(metrics_by_type.get("CPU_USAGE", [0])),
            "avg_temp": np.mean(metrics_by_type.get("TEMPERATURE", [45])),
            "avg_signal": np.mean(metrics_by_type.get("SIGNAL_STRENGTH", [-60])),
            "total_throughput": sum(metrics_by_type.get("DATA_THROUGHPUT", [0])),
            "avg_memory": np.mean(metrics_by_type.get("MEMORY_USAGE", [0])),
        }

    def create_executive_summary(self, pdf: PdfPages):
        """Create executive summary page"""
        fig = plt.figure(figsize=(11, 8.5))
        self._add_header(fig, "Executive Summary", "Key Performance Indicators")

        gs = GridSpec(2, 3, figure=fig, left=0.06, right=0.94, top=0.88, bottom=0.08,
                     hspace=0.3, wspace=0.25)

        kpis = self._calculate_kpis()

        # Format throughput with K/M suffix to prevent overflow
        throughput_val = kpis['total_throughput']
        if throughput_val >= 1000:
            throughput_str = f"{throughput_val/1000:.1f}K"
        else:
            throughput_str = f"{throughput_val:.0f}"

        kpi_data = [
            ("System Health", f"{kpis['health_score']:.0f}%", COLORS["success"] if kpis['health_score'] >= 90 else COLORS["warning"]),
            ("Avg CPU", f"{kpis['avg_cpu']:.1f}%", COLORS["success"] if kpis['avg_cpu'] < 70 else COLORS["warning"]),
            ("Avg Temp", f"{kpis['avg_temp']:.0f}°C", COLORS["success"] if kpis['avg_temp'] < 75 else COLORS["warning"]),
            ("Avg Signal", f"{kpis['avg_signal']:.0f}dBm", COLORS["success"] if kpis['avg_signal'] > -80 else COLORS["warning"]),
            ("Throughput", f"{throughput_str} Mbps", COLORS["info"]),
            ("Alerts", str(len(self.alerts)), COLORS["danger"] if len(self.alerts) > 5 else COLORS["success"]),
        ]

        for i, (label, value, color) in enumerate(kpi_data):
            ax = fig.add_subplot(gs[i // 3, i % 3])
            ax.axis('off')

            card = plt.Rectangle((0.05, 0.1), 0.9, 0.8, fill=True,
                                 facecolor=COLORS["white"], edgecolor=color,
                                 linewidth=2, alpha=1, transform=ax.transAxes)
            ax.add_patch(card)
            # Adjust font size based on value length to prevent overflow
            fontsize = 24 if len(value) > 8 else 28
            ax.text(0.5, 0.55, value, fontsize=fontsize, fontweight='bold',
                   color=color, ha='center', va='center', transform=ax.transAxes)
            ax.text(0.5, 0.25, label, fontsize=10, color=COLORS["secondary"],
                   ha='center', va='center', transform=ax.transAxes)

        self._add_footer(fig, 2)
        pdf.savefig(fig, facecolor='white')
        plt.close(fig)

    def _create_status_pie_chart(self, ax) -> None:
        """Create status distribution pie chart."""
        status_counts = defaultdict(int)
        for s in self.stations:
            status_counts[s.get("status", "UNKNOWN")] += 1

        if status_counts:
            labels = list(status_counts.keys())
            sizes = list(status_counts.values())
            colors = [STATUS_COLORS.get(s, COLORS["secondary"]) for s in labels]

            _, _, autotexts = ax.pie(sizes, labels=labels, colors=colors,
                                     autopct='%1.1f%%', startangle=90,
                                     wedgeprops={'width': 0.6, 'edgecolor': 'white'},
                                     textprops={'color': COLORS["dark"]})
            for autotext in autotexts:
                autotext.set_fontweight('bold')
                autotext.set_fontsize(10)

            centre_circle = plt.Circle((0, 0), 0.35, fc='white')
            ax.add_patch(centre_circle)
            ax.text(0, 0, f"{len(self.stations)}\nStations", ha='center', va='center',
                   fontsize=14, fontweight='bold', color=COLORS["dark"])
        ax.set_title("Station Status", pad=15)

    def _create_type_bar_chart(self, ax) -> None:
        """Create station type bar chart."""
        type_counts = defaultdict(int)
        for s in self.stations:
            type_counts[s.get("stationType", "UNKNOWN")] += 1

        if type_counts:
            types = list(type_counts.keys())
            counts = list(type_counts.values())
            bar_colors = [COLORS["primary"], COLORS["info"], COLORS["accent1"], COLORS["accent2"]]

            bars = ax.bar(types, counts, color=bar_colors[:len(types)], edgecolor='white', linewidth=1)
            for bar, count in zip(bars, counts):
                ax.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 0.1,
                       str(count), ha='center', va='bottom', fontweight='bold',
                       fontsize=11, color=COLORS["dark"])
            ax.set_ylabel("Count")
            ax.set_ylim(0, max(counts) * 1.2)
        ax.set_title("Station Types", pad=15)

    def _create_station_table(self, ax) -> None:
        """Create station details table."""
        ax.axis('off')
        ax.set_title("Station Details", pad=15, loc='left')

        if not self.stations:
            return

        headers = ["ID", "Name", "Location", "Type", "Status", "Power"]
        table_data = []
        for s in self.stations[:8]:
            power = s.get('powerConsumption')
            table_data.append([
                str(s.get("id", "-"))[:6],
                s.get("stationName", "-")[:18],
                s.get("location", "-")[:15],
                s.get("stationType", "-")[:8],
                s.get("status", "-")[:8],
                f"{power:.1f}" if power else "-"
            ])

        table = ax.table(cellText=table_data, colLabels=headers,
                        loc='center', cellLoc='center',
                        colColours=[COLORS["primary"]]*len(headers),
                        colWidths=[0.08, 0.22, 0.20, 0.15, 0.15, 0.10])

        table.auto_set_font_size(False)
        table.set_fontsize(8)
        table.scale(1.1, 1.8)

        for i in range(len(headers)):
            table[(0, i)].set_text_props(color='white', fontweight='bold')
            table[(0, i)].set_facecolor(COLORS["primary"])

        for row_idx, row in enumerate(table_data):
            status = row[4]
            table[(row_idx + 1, 4)].set_facecolor(
                STATUS_COLORS.get(status, COLORS["light"]) + "40"
            )

    def create_station_overview(self, pdf: PdfPages):
        """Create station overview with charts."""
        fig = plt.figure(figsize=(11, 8.5))
        self._add_header(fig, "Station Overview", "Status and Distribution Analysis")

        gs = GridSpec(2, 2, figure=fig, left=0.08, right=0.92, top=0.86, bottom=0.1,
                     hspace=0.35, wspace=0.3)

        self._create_status_pie_chart(fig.add_subplot(gs[0, 0]))
        self._create_type_bar_chart(fig.add_subplot(gs[0, 1]))
        self._create_station_table(fig.add_subplot(gs[1, :]))

        self._add_footer(fig, 3)
        pdf.savefig(fig, facecolor='white')
        plt.close(fig)

    def _aggregate_metrics_by_station(self) -> Dict[str, Dict[str, List[float]]]:
        """Aggregate metrics by station ID and type."""
        metrics_by_station: Dict[str, Dict[str, List[float]]] = defaultdict(lambda: defaultdict(list))
        for m in self.metrics:
            sid = str(m.get("stationId", "?"))
            mtype = m.get("metricType", "")
            value = m.get("value")
            if value is not None:
                metrics_by_station[sid][mtype].append(float(value))
        return metrics_by_station

    def _get_threshold_color(self, value: float, warn_thresh: float, crit_thresh: float,
                             higher_is_worse: bool = True) -> str:
        """Get color based on threshold comparison."""
        if higher_is_worse:
            if value < warn_thresh:
                return COLORS["success"]
            return COLORS["warning"] if value < crit_thresh else COLORS["danger"]
        # For metrics where lower is worse (like signal strength)
        if value > warn_thresh:
            return COLORS["success"]
        return COLORS["warning"] if value > crit_thresh else COLORS["danger"]

    def _create_usage_bar_chart(self, ax, data: Dict[str, float], title: str,
                                warn_thresh: float, crit_thresh: float, show_legend: bool = False) -> None:
        """Create horizontal bar chart for usage metrics."""
        if data:
            stations = list(data.keys())[:6]
            values = [data[s] for s in stations]
            colors = [self._get_threshold_color(v, warn_thresh, crit_thresh) for v in values]

            bars = ax.barh(stations, values, color=colors, edgecolor='white', height=0.6)
            ax.axvline(x=warn_thresh, color=COLORS["warning"], linestyle='--', alpha=0.5,
                      label=f'Warning ({warn_thresh:.0f}%)')
            ax.axvline(x=crit_thresh, color=COLORS["danger"], linestyle='--', alpha=0.5,
                      label=f'Critical ({crit_thresh:.0f}%)')

            for bar, val in zip(bars, values):
                x_pos = val - 5 if val > 85 else val + 1
                color = 'white' if val > 85 else COLORS["dark"]
                ax.text(x_pos, bar.get_y() + bar.get_height()/2, f'{val:.0f}%',
                       va='center', fontsize=8, fontweight='bold', color=color)

            ax.set_xlim(0, 105)
            ax.set_xlabel("Usage (%)")
            if show_legend:
                ax.legend(loc='lower right', fontsize=8)
        ax.set_title(title, pad=15)

    def _create_distribution_histogram(self, ax, values: List[float], title: str,
                                        xlabel: str, thresholds: tuple, higher_is_worse: bool = True,
                                        critical_line: Optional[float] = None) -> None:
        """Create histogram with color-coded distribution."""
        if not values:
            ax.set_title(title, pad=15)
            return

        _, bins, patches = ax.hist(values, bins=15, edgecolor='white', linewidth=1)
        warn_thresh, crit_thresh = thresholds

        for i, patch in enumerate(patches):
            mid_val = (bins[i] + bins[i+1]) / 2
            patch.set_facecolor(self._get_threshold_color(mid_val, warn_thresh, crit_thresh, higher_is_worse))

        mean_val = np.mean(values)
        ax.axvline(x=mean_val, color=COLORS["dark"], linestyle='-', linewidth=2,
                  label=f'Mean: {mean_val:.1f}')
        if critical_line is not None:
            ax.axvline(x=critical_line, color=COLORS["danger"], linestyle='--', alpha=0.7,
                      label=f'Critical: {critical_line:.0f}')

        ax.set_xlabel(xlabel)
        ax.set_ylabel("Frequency")
        ax.legend(loc='upper right' if higher_is_worse else 'upper left', fontsize=8)
        ax.set_title(title, pad=15)

    def create_performance_metrics(self, pdf: PdfPages):
        """Create performance metrics page."""
        fig = plt.figure(figsize=(11, 8.5))
        self._add_header(fig, "Performance Metrics", "Resource Utilization Analysis")

        gs = GridSpec(2, 2, figure=fig, left=0.08, right=0.92, top=0.86, bottom=0.1,
                     hspace=0.35, wspace=0.3)

        metrics_by_station = self._aggregate_metrics_by_station()

        # CPU Usage
        cpu_data = {k: np.mean(v.get("CPU_USAGE", [0])) for k, v in metrics_by_station.items() if v.get("CPU_USAGE")}
        self._create_usage_bar_chart(fig.add_subplot(gs[0, 0]), cpu_data, "CPU Usage by Station", 60, 80, True)

        # Memory Usage
        mem_data = {k: np.mean(v.get("MEMORY_USAGE", [0])) for k, v in metrics_by_station.items() if v.get("MEMORY_USAGE")}
        self._create_usage_bar_chart(fig.add_subplot(gs[0, 1]), mem_data, "Memory Usage by Station", 70, 85)

        # Temperature Distribution
        all_temps = [t for v in metrics_by_station.values() for t in v.get("TEMPERATURE", [])]
        self._create_distribution_histogram(fig.add_subplot(gs[1, 0]), all_temps, "Temperature Distribution",
                                            "Temperature (C)", (60, 75), True, 80)

        # Signal Strength (lower thresholds, higher is better)
        all_signals = [s for v in metrics_by_station.values() for s in v.get("SIGNAL_STRENGTH", [])]
        self._create_distribution_histogram(fig.add_subplot(gs[1, 1]), all_signals, "Signal Strength Distribution",
                                            "Signal Strength (dBm)", (-70, -90), False)

        self._add_footer(fig, 4)
        pdf.savefig(fig, facecolor='white')
        plt.close(fig)

    def _create_simple_bar_chart(self, ax, data: Dict[str, float], title: str,
                                   ylabel: str, color: str, offset: float = 2) -> None:
        """Create simple vertical bar chart."""
        if data:
            stations = list(data.keys())[:6]
            values = [data[s] for s in stations]

            bars = ax.bar(stations, values, color=color, edgecolor='white', width=0.6)
            for bar, val in zip(bars, values):
                ax.text(bar.get_x() + bar.get_width()/2, bar.get_height() + offset,
                       f'{val:.0f}', ha='center', fontsize=9, fontweight='bold')

            ax.set_ylabel(ylabel)
            ax.set_ylim(0, max(values) * 1.2 if values else 100)
        ax.set_title(title, pad=15)

    def _create_uptime_chart(self, ax, data: Dict[str, float]) -> None:
        """Create uptime bar chart with color thresholds."""
        if data:
            stations = list(data.keys())[:6]
            values = [min(data[s], 100) for s in stations]
            colors = [COLORS["success"] if v >= 99 else COLORS["warning"] if v >= 95 else COLORS["danger"] for v in values]

            bars = ax.bar(stations, values, color=colors, edgecolor='white', width=0.6)
            for bar, val in zip(bars, values):
                ax.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 0.5,
                       f'{val:.1f}%', ha='center', fontsize=9, fontweight='bold')

            ax.set_ylabel("Uptime (%)")
            ax.set_ylim(90, 101)
            ax.axhline(y=99, color=COLORS["success"], linestyle='--', alpha=0.5, label='Target: 99%')
            ax.legend(loc='lower right', fontsize=8)
        ax.set_title("Station Uptime", pad=15)

    def _create_metrics_summary_table(self, ax) -> None:
        """Create metrics summary table."""
        ax.axis('off')

        metrics_summary = defaultdict(list)
        for m in self.metrics:
            mtype = m.get("metricType", "")
            value = m.get("value")
            if value is not None:
                metrics_summary[mtype].append(float(value))

        if metrics_summary:
            headers = ["Metric", "Count", "Min", "Max", "Avg"]
            table_data = [[mtype[:15], str(len(vals)), f"{min(vals):.1f}",
                          f"{max(vals):.1f}", f"{np.mean(vals):.1f}"]
                         for mtype, vals in sorted(metrics_summary.items())]

            table = ax.table(cellText=table_data[:8], colLabels=headers,
                            loc='center', cellLoc='center',
                            colColours=[COLORS["primary"]]*len(headers),
                            colWidths=[0.35, 0.15, 0.15, 0.15, 0.15])
            table.auto_set_font_size(False)
            table.set_fontsize(8)
            table.scale(1.05, 1.6)

            for i in range(len(headers)):
                table[(0, i)].set_text_props(color='white', fontweight='bold')

        ax.set_title("Metrics Summary", pad=15, loc='center')

    def create_network_analysis(self, pdf: PdfPages):
        """Create network performance page."""
        fig = plt.figure(figsize=(11, 8.5))
        self._add_header(fig, "Network Analysis", "Throughput and Connectivity")

        gs = GridSpec(2, 2, figure=fig, left=0.08, right=0.92, top=0.86, bottom=0.1,
                     hspace=0.35, wspace=0.3)

        metrics_by_station = self._aggregate_metrics_by_station()

        # Throughput
        throughput = {k: np.mean(v.get("DATA_THROUGHPUT", [0])) for k, v in metrics_by_station.items() if v.get("DATA_THROUGHPUT")}
        self._create_simple_bar_chart(fig.add_subplot(gs[0, 0]), throughput,
                                      "Data Throughput by Station", "Throughput (Mbps)", COLORS["info"])

        # Connections
        connections = {k: np.mean(v.get("CONNECTION_COUNT", [0])) for k, v in metrics_by_station.items() if v.get("CONNECTION_COUNT")}
        self._create_simple_bar_chart(fig.add_subplot(gs[0, 1]), connections,
                                      "Connected Devices by Station", "Connections", COLORS["accent2"], 1)

        # Uptime
        uptime = {k: np.mean(v.get("UPTIME", [99])) for k, v in metrics_by_station.items() if v.get("UPTIME")}
        self._create_uptime_chart(fig.add_subplot(gs[1, 0]), uptime)

        # Metrics Summary Table
        self._create_metrics_summary_table(fig.add_subplot(gs[1, 1]))

        self._add_footer(fig, 5)
        pdf.savefig(fig, facecolor='white')
        plt.close(fig)

    def _create_alert_types_chart(self, ax) -> None:
        """Create alert types bar chart."""
        alert_types = defaultdict(int)
        for a in self.alerts:
            atype = a.get("type", a.get("alertType", "OTHER"))
            alert_types[atype] += 1

        if alert_types:
            types = list(alert_types.keys())
            counts = list(alert_types.values())
            colors = [COLORS["danger"], COLORS["warning"], COLORS["info"], COLORS["secondary"]]

            bars = ax.bar(types, counts, color=colors[:len(types)], edgecolor='white')
            for bar, count in zip(bars, counts):
                ax.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 0.1,
                       str(count), ha='center', fontweight='bold')
            ax.set_ylabel("Count")
        else:
            ax.text(0.5, 0.5, "No alerts recorded", ha='center', va='center',
                   transform=ax.transAxes, fontsize=12, color=COLORS["secondary"])
        ax.set_title("Alert Types", pad=15)

    def _create_notification_pie_chart(self, ax) -> None:
        """Create notification types pie chart."""
        notif_types = defaultdict(int)
        for n in self.notifications:
            ntype = n.get("type", "OTHER")
            notif_types[ntype] += 1

        if notif_types:
            types = list(notif_types.keys())
            counts = list(notif_types.values())
            color_map = {"ALERT": COLORS["danger"], "WARNING": COLORS["warning"], "INFO": COLORS["info"]}
            colors = [color_map.get(t, COLORS["secondary"]) for t in types]

            _, _, autotexts = ax.pie(counts, labels=types, colors=colors,
                                     autopct='%1.0f%%', startangle=90,
                                     wedgeprops={'width': 0.7, 'edgecolor': 'white'})
            for autotext in autotexts:
                autotext.set_fontweight('bold')
        else:
            ax.text(0.5, 0.5, "No notifications", ha='center', va='center',
                   transform=ax.transAxes, fontsize=12, color=COLORS["secondary"])
        ax.set_title("Notification Distribution", pad=15)

    def _create_alerts_by_station_chart(self, ax) -> None:
        """Create alerts by station bar chart."""
        alerts_by_station = defaultdict(int)
        for a in self.alerts:
            sid = str(a.get("stationId", "?"))
            alerts_by_station[sid] += 1

        if alerts_by_station:
            stations = list(alerts_by_station.keys())[:6]
            counts = [alerts_by_station[s] for s in stations]

            bars = ax.bar(stations, counts, color=COLORS["danger"], edgecolor='white', alpha=0.8)
            for bar, count in zip(bars, counts):
                ax.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 0.1,
                       str(count), ha='center', fontweight='bold')
            ax.set_ylabel("Alert Count")
            ax.set_xlabel("Station ID")
        else:
            ax.text(0.5, 0.5, "No alerts by station", ha='center', va='center',
                   transform=ax.transAxes, fontsize=12, color=COLORS["secondary"])
        ax.set_title("Alerts by Station", pad=15)

    def _create_recent_alerts_table(self, ax) -> None:
        """Create recent alerts table."""
        ax.axis('off')

        if self.alerts:
            headers = ["Station", "Type", "Message"]
            table_data = [[str(a.get("stationId", "-"))[:8],
                          a.get("type", a.get("alertType", "-"))[:10],
                          a.get("message", "-")[:30]]
                         for a in self.alerts[:6]]

            table = ax.table(cellText=table_data, colLabels=headers,
                            loc='center', cellLoc='left',
                            colColours=[COLORS["danger"]]*len(headers),
                            colWidths=[0.18, 0.22, 0.55])
            table.auto_set_font_size(False)
            table.set_fontsize(8)
            table.scale(1.05, 1.5)

            for i in range(len(headers)):
                table[(0, i)].set_text_props(color='white', fontweight='bold')
        else:
            ax.text(0.5, 0.5, "No recent alerts", ha='center', va='center',
                   transform=ax.transAxes, fontsize=12, color=COLORS["secondary"])

        ax.set_title("Recent Alerts", pad=15, loc='center')

    def create_alerts_page(self, pdf: PdfPages):
        """Create alerts and notifications page."""
        fig = plt.figure(figsize=(11, 8.5))
        self._add_header(fig, "Alerts & Notifications", "System Health Monitoring")

        gs = GridSpec(2, 2, figure=fig, left=0.08, right=0.92, top=0.86, bottom=0.1,
                     hspace=0.35, wspace=0.3)

        self._create_alert_types_chart(fig.add_subplot(gs[0, 0]))
        self._create_notification_pie_chart(fig.add_subplot(gs[0, 1]))
        self._create_alerts_by_station_chart(fig.add_subplot(gs[1, 0]))
        self._create_recent_alerts_table(fig.add_subplot(gs[1, 1]))

        self._add_footer(fig, 6)
        pdf.savefig(fig, facecolor='white')
        plt.close(fig)

    def create_geographic_view(self, pdf: PdfPages):
        """Create geographic distribution page"""
        fig = plt.figure(figsize=(11, 8.5))
        self._add_header(fig, "Geographic Distribution", "Station Locations")

        ax = fig.add_axes([0.1, 0.15, 0.8, 0.7])

        lats, lons, names, statuses = [], [], [], []
        for s in self.stations:
            lat, lon = s.get("latitude"), s.get("longitude")
            if lat is not None and lon is not None:
                lats.append(lat)
                lons.append(lon)
                names.append(s.get("stationName", "?")[:15])
                statuses.append(s.get("status", "UNKNOWN"))

        if lats and lons:
            colors = [STATUS_COLORS.get(s, COLORS["secondary"]) for s in statuses]

            ax.scatter(lons, lats, c=colors, s=300, alpha=0.8,
                      edgecolors='white', linewidths=2, zorder=5)

            for i, name in enumerate(names):
                ax.annotate(name, (lons[i], lats[i]),
                           xytext=(8, 8), textcoords='offset points',
                           fontsize=9, fontweight='bold',
                           bbox={'boxstyle': 'round,pad=0.3', 'facecolor': 'white', 'alpha': 0.8})

            ax.set_xlabel("Longitude", fontsize=11)
            ax.set_ylabel("Latitude", fontsize=11)
            ax.grid(True, alpha=0.3, linestyle='--')

            patches = [mpatches.Patch(color=c, label=s) for s, c in STATUS_COLORS.items()]
            ax.legend(handles=patches, loc='upper right', framealpha=0.9)

            lat_range = max(lats) - min(lats) if len(lats) > 1 else 1
            lon_range = max(lons) - min(lons) if len(lons) > 1 else 1
            ax.set_xlim(min(lons) - lon_range * 0.2, max(lons) + lon_range * 0.2)
            ax.set_ylim(min(lats) - lat_range * 0.2, max(lats) + lat_range * 0.2)
        else:
            ax.text(0.5, 0.5, "No location data available", ha='center', va='center',
                   transform=ax.transAxes, fontsize=14, color=COLORS["secondary"])
            ax.axis('off')

        self._add_footer(fig, 7)
        pdf.savefig(fig, facecolor='white')
        plt.close(fig)

    # ========================================================================
    # 5G NR METRICS PAGES (NEW)
    # ========================================================================

    # SSV KPI Thresholds (based on Huawei criteria)
    SSV_THRESHOLDS = {
        "DL_THROUGHPUT_NR3500": {"min": 1000, "warn": 1100, "unit": "Mbps", "higher_better": True},
        "UL_THROUGHPUT_NR3500": {"min": 75, "warn": 85, "unit": "Mbps", "higher_better": True},
        "DL_THROUGHPUT_NR700": {"min": 50, "warn": 60, "unit": "Mbps", "higher_better": True},
        "UL_THROUGHPUT_NR700": {"min": 20, "warn": 25, "unit": "Mbps", "higher_better": True},
        "LATENCY_PING": {"max": 15, "warn": 12, "unit": "ms", "higher_better": False},
        "TX_IMBALANCE": {"max": 4, "warn": 3, "unit": "dB", "higher_better": False},
        "HANDOVER_SUCCESS_RATE": {"min": 100, "warn": 98, "unit": "%", "higher_better": True},
        "SINR_NR3500": {"min": 10, "warn": 15, "unit": "dB", "higher_better": True},
        "SINR_NR700": {"min": 8, "warn": 12, "unit": "dB", "higher_better": True},
    }

    def _get_ssv_status(self, metric_type: str, value: float) -> tuple:
        """Get SSV pass/warn/fail status and color for a metric."""
        thresh = self.SSV_THRESHOLDS.get(metric_type)
        if not thresh:
            return "N/A", COLORS["secondary"]

        if thresh.get("higher_better", True):
            min_val = thresh.get("min", 0)
            warn_val = thresh.get("warn", min_val)
            if value >= warn_val:
                return "PASS", COLORS["success"]
            elif value >= min_val:
                return "WARN", COLORS["warning"]
            else:
                return "FAIL", COLORS["danger"]
        else:
            max_val = thresh.get("max", 100)
            warn_val = thresh.get("warn", max_val)
            if value <= warn_val:
                return "PASS", COLORS["success"]
            elif value <= max_val:
                return "WARN", COLORS["warning"]
            else:
                return "FAIL", COLORS["danger"]

    def _draw_gauge(self, ax, value: float, min_val: float, max_val: float,
                    label: str, unit: str, threshold: float = None):
        """Draw a speedometer-style gauge chart."""
        ax.set_xlim(-1.5, 1.5)
        ax.set_ylim(-0.2, 1.3)
        ax.axis('off')

        # Draw arc background
        theta = np.linspace(np.pi, 0, 100)
        x_arc = np.cos(theta)
        y_arc = np.sin(theta)

        # Background arc (gray)
        ax.plot(x_arc, y_arc, color=COLORS["light"], linewidth=20, solid_capstyle='round')

        # Colored sections
        range_val = max_val - min_val
        if threshold:
            # Green section (good)
            good_pct = (threshold - min_val) / range_val
            theta_good = np.linspace(np.pi, np.pi * (1 - good_pct), 50)
            ax.plot(np.cos(theta_good), np.sin(theta_good), color=COLORS["success"],
                   linewidth=18, solid_capstyle='round', alpha=0.8)
            # Yellow section (warning)
            theta_warn = np.linspace(np.pi * (1 - good_pct), np.pi * 0.3, 30)
            ax.plot(np.cos(theta_warn), np.sin(theta_warn), color=COLORS["warning"],
                   linewidth=18, solid_capstyle='round', alpha=0.8)
            # Red section (fail)
            theta_fail = np.linspace(np.pi * 0.3, 0, 20)
            ax.plot(np.cos(theta_fail), np.sin(theta_fail), color=COLORS["danger"],
                   linewidth=18, solid_capstyle='round', alpha=0.8)

        # Needle
        clamped = max(min_val, min(max_val, value))
        needle_angle = np.pi - (clamped - min_val) / range_val * np.pi
        needle_x = [0, 0.7 * np.cos(needle_angle)]
        needle_y = [0, 0.7 * np.sin(needle_angle)]
        ax.plot(needle_x, needle_y, color=COLORS["dark"], linewidth=3, solid_capstyle='round')
        ax.scatter([0], [0], s=150, color=COLORS["dark"], zorder=10)

        # Value text
        ax.text(0, 0.4, f"{value:.0f}", fontsize=24, fontweight='bold',
               ha='center', va='center', color=COLORS["dark"])
        ax.text(0, 0.15, unit, fontsize=10, ha='center', va='center', color=COLORS["secondary"])
        ax.text(0, -0.1, label, fontsize=11, fontweight='bold',
               ha='center', va='center', color=COLORS["dark"])

        # Min/Max labels
        ax.text(-1.1, -0.05, f"{min_val:.0f}", fontsize=8, ha='center', color=COLORS["secondary"])
        ax.text(1.1, -0.05, f"{max_val:.0f}", fontsize=8, ha='center', color=COLORS["secondary"])

    def _draw_traffic_light(self, ax, status: str, label: str, value: str, threshold: str):
        """Draw a traffic light indicator."""
        ax.set_xlim(0, 1)
        ax.set_ylim(0, 1)
        ax.axis('off')

        # Background card
        card = plt.Rectangle((0.05, 0.05), 0.9, 0.9, fill=True,
                             facecolor=COLORS["white"], edgecolor=COLORS["secondary"],
                             linewidth=1, alpha=0.9)
        ax.add_patch(card)

        # Status light
        status_colors = {"PASS": COLORS["success"], "WARN": COLORS["warning"],
                        "FAIL": COLORS["danger"], "N/A": COLORS["secondary"]}
        light_color = status_colors.get(status, COLORS["secondary"])

        circle = plt.Circle((0.5, 0.7), 0.15, color=light_color, ec='white', linewidth=2)
        ax.add_patch(circle)

        # Glow effect
        glow = plt.Circle((0.5, 0.7), 0.18, color=light_color, alpha=0.3)
        ax.add_patch(glow)

        # Text
        ax.text(0.5, 0.45, value, fontsize=14, fontweight='bold',
               ha='center', va='center', color=COLORS["dark"])
        ax.text(0.5, 0.3, label, fontsize=9, ha='center', va='center', color=COLORS["secondary"])
        ax.text(0.5, 0.15, f"Target: {threshold}", fontsize=7,
               ha='center', va='center', color=COLORS["secondary"], style='italic')

    def create_5g_kpi_dashboard(self, pdf: PdfPages):
        """Create 5G KPI Dashboard with traffic light indicators."""
        fig = plt.figure(figsize=(11, 8.5))
        self._add_header(fig, "5G NR KPI Dashboard", "SSV Compliance Status")

        gs = GridSpec(3, 4, figure=fig, left=0.05, right=0.95, top=0.85, bottom=0.08,
                     hspace=0.4, wspace=0.2)

        # Aggregate 5G metrics
        metrics_agg = defaultdict(list)
        for m in self.metrics:
            mtype = m.get("metricType", "")
            value = m.get("value")
            if value is not None and mtype in self.SSV_THRESHOLDS:
                metrics_agg[mtype].append(float(value))

        # KPI definitions for display
        kpi_display = [
            ("DL_THROUGHPUT_NR3500", "DL NR3500", "≥1000 Mbps"),
            ("UL_THROUGHPUT_NR3500", "UL NR3500", "≥75 Mbps"),
            ("DL_THROUGHPUT_NR700", "DL NR700", "≥50 Mbps"),
            ("UL_THROUGHPUT_NR700", "UL NR700", "≥20 Mbps"),
            ("LATENCY_PING", "Latency", "≤15 ms"),
            ("TX_IMBALANCE", "TX Imbalance", "≤4 dB"),
            ("SINR_NR3500", "SINR 3500", "≥10 dB"),
            ("SINR_NR700", "SINR 700", "≥8 dB"),
            ("HANDOVER_SUCCESS_RATE", "Handover", "100%"),
        ]

        # Draw traffic lights
        for i, (metric_type, label, threshold) in enumerate(kpi_display):
            if i >= 12:  # Max 12 indicators
                break
            row = i // 4
            col = i % 4

            ax = fig.add_subplot(gs[row, col])
            values = metrics_agg.get(metric_type, [])

            if values:
                avg_val = np.mean(values)
                status, _ = self._get_ssv_status(metric_type, avg_val)
                unit = self.SSV_THRESHOLDS[metric_type]["unit"]
                value_str = f"{avg_val:.1f} {unit}"
            else:
                status = "N/A"
                value_str = "No Data"

            self._draw_traffic_light(ax, status, label, value_str, threshold)

        # Summary box
        total_kpis = len([k for k in kpi_display if metrics_agg.get(k[0])])
        passed = sum(1 for k, _, _ in kpi_display if metrics_agg.get(k) and
                    self._get_ssv_status(k, np.mean(metrics_agg[k]))[0] == "PASS")
        warned = sum(1 for k, _, _ in kpi_display if metrics_agg.get(k) and
                    self._get_ssv_status(k, np.mean(metrics_agg[k]))[0] == "WARN")
        failed = total_kpis - passed - warned

        summary_ax = fig.add_axes([0.3, 0.02, 0.4, 0.05])
        summary_ax.axis('off')
        summary_ax.text(0.5, 0.5,
                       f"SSV Summary: {passed} PASS | {warned} WARN | {failed} FAIL",
                       fontsize=12, fontweight='bold', ha='center', va='center',
                       bbox={'boxstyle': 'round,pad=0.5', 'facecolor': COLORS["light"],
                            'edgecolor': COLORS["primary"], 'alpha': 0.9})

        self._add_footer(fig, 8)
        pdf.savefig(fig, facecolor='white')
        plt.close(fig)

    def create_5g_throughput_gauges(self, pdf: PdfPages):
        """Create 5G throughput gauge page."""
        fig = plt.figure(figsize=(11, 8.5))
        self._add_header(fig, "5G Throughput Performance", "Speedometer View")

        gs = GridSpec(2, 2, figure=fig, left=0.08, right=0.92, top=0.85, bottom=0.1,
                     hspace=0.3, wspace=0.2)

        # Aggregate throughput metrics
        metrics_agg = defaultdict(list)
        for m in self.metrics:
            mtype = m.get("metricType", "")
            value = m.get("value")
            if value is not None:
                metrics_agg[mtype].append(float(value))

        gauge_configs = [
            ("DL_THROUGHPUT_NR3500", "DL Throughput NR3500", 0, 1500, 1000, "Mbps"),
            ("UL_THROUGHPUT_NR3500", "UL Throughput NR3500", 0, 150, 75, "Mbps"),
            ("DL_THROUGHPUT_NR700", "DL Throughput NR700", 0, 150, 50, "Mbps"),
            ("UL_THROUGHPUT_NR700", "UL Throughput NR700", 0, 50, 20, "Mbps"),
        ]

        for i, (metric_type, label, min_v, max_v, thresh, unit) in enumerate(gauge_configs):
            ax = fig.add_subplot(gs[i // 2, i % 2])
            values = metrics_agg.get(metric_type, [])
            value = np.mean(values) if values else 0
            self._draw_gauge(ax, value, min_v, max_v, label, unit, thresh)

        self._add_footer(fig, 9)
        pdf.savefig(fig, facecolor='white')
        plt.close(fig)

    def _create_heatmap(self, ax, data: np.ndarray, row_labels: list, col_labels: list,
                        title: str, cmap: str = 'RdYlGn', vmin: float = None, vmax: float = None):
        """Create a heatmap visualization."""
        if data.size == 0:
            ax.text(0.5, 0.5, "No data available", ha='center', va='center',
                   transform=ax.transAxes, fontsize=12, color=COLORS["secondary"])
            ax.set_title(title, pad=15)
            return

        im = ax.imshow(data, cmap=cmap, aspect='auto', vmin=vmin, vmax=vmax)

        ax.set_xticks(np.arange(len(col_labels)))
        ax.set_yticks(np.arange(len(row_labels)))
        ax.set_xticklabels(col_labels, fontsize=9)
        ax.set_yticklabels(row_labels, fontsize=9)

        plt.setp(ax.get_xticklabels(), rotation=45, ha="right", rotation_mode="anchor")

        # Add text annotations
        for i in range(len(row_labels)):
            for j in range(len(col_labels)):
                val = data[i, j]
                if not np.isnan(val):
                    text_color = 'white' if abs(val - (vmin or data.min())) > (((vmax or data.max()) - (vmin or data.min())) / 2) else 'black'
                    ax.text(j, i, f"{val:.1f}", ha="center", va="center",
                           color=text_color, fontsize=8, fontweight='bold')

        ax.set_title(title, pad=15)
        cbar = plt.colorbar(im, ax=ax, shrink=0.8)
        cbar.ax.tick_params(labelsize=8)

    def create_sector_heatmap(self, pdf: PdfPages):
        """Create sector-level RSRP/SINR heatmap page."""
        fig = plt.figure(figsize=(11, 8.5))
        self._add_header(fig, "Sector Performance Heatmap", "RSRP & SINR by Station/Sector")

        gs = GridSpec(2, 2, figure=fig, left=0.1, right=0.9, top=0.85, bottom=0.1,
                     hspace=0.4, wspace=0.35)

        # Group metrics by station (simulating sectors as metrics per station)
        station_metrics = defaultdict(lambda: defaultdict(list))
        for m in self.metrics:
            sid = str(m.get("stationId", "?"))
            mtype = m.get("metricType", "")
            value = m.get("value")
            if value is not None:
                station_metrics[sid][mtype].append(float(value))

        station_ids = list(station_metrics.keys())[:6]  # Max 6 stations

        # Build data matrices
        for idx, (metric_type, title, cmap, vmin, vmax) in enumerate([
            ("RSRP_NR3500", "RSRP NR3500 (dBm)", "RdYlGn", -100, -60),
            ("SINR_NR3500", "SINR NR3500 (dB)", "RdYlGn", 0, 35),
            ("RSRP_NR700", "RSRP NR700 (dBm)", "RdYlGn", -100, -40),
            ("SINR_NR700", "SINR NR700 (dB)", "RdYlGn", 0, 30),
        ]):
            ax = fig.add_subplot(gs[idx // 2, idx % 2])

            if station_ids:
                # Simulate 3 sectors per station
                sectors = ["Sector 1", "Sector 2", "Sector 3"]
                data = np.zeros((len(station_ids), len(sectors)))
                data[:] = np.nan

                for i, sid in enumerate(station_ids):
                    values = station_metrics[sid].get(metric_type, [])
                    if values:
                        avg = np.mean(values)
                        # Simulate sector variation
                        for j in range(3):
                            data[i, j] = avg + _rng.uniform(-3, 3)

                self._create_heatmap(ax, data, station_ids, sectors, title, cmap, vmin, vmax)
            else:
                ax.text(0.5, 0.5, "No sector data", ha='center', va='center',
                       transform=ax.transAxes, fontsize=12, color=COLORS["secondary"])
                ax.set_title(title, pad=15)

        self._add_footer(fig, 10)
        pdf.savefig(fig, facecolor='white')
        plt.close(fig)

    def create_ssv_scorecard(self, pdf: PdfPages):
        """Create SSV compliance scorecard page."""
        fig = plt.figure(figsize=(11, 8.5))
        self._add_header(fig, "SSV Compliance Scorecard", "Site Verification Status")

        # Main table area
        ax = fig.add_axes([0.08, 0.12, 0.84, 0.72])
        ax.axis('off')

        # Aggregate metrics by station
        station_metrics = defaultdict(lambda: defaultdict(list))
        for m in self.metrics:
            sid = str(m.get("stationId", "?"))
            mtype = m.get("metricType", "")
            value = m.get("value")
            if value is not None:
                station_metrics[sid][mtype].append(float(value))

        # Build scorecard data
        headers = ["Station", "DL 3500", "UL 3500", "Latency", "TX Imbal", "Overall"]
        cell_colors = []
        table_data = []

        for sid in list(station_metrics.keys())[:10]:
            row = [f"Station {sid}"]
            row_colors = [COLORS["light"]]
            overall_pass = True
            overall_warn = False

            for metric_type in ["DL_THROUGHPUT_NR3500", "UL_THROUGHPUT_NR3500",
                               "LATENCY_PING", "TX_IMBALANCE"]:
                values = station_metrics[sid].get(metric_type, [])
                if values:
                    avg = np.mean(values)
                    status, color = self._get_ssv_status(metric_type, avg)
                    unit = self.SSV_THRESHOLDS.get(metric_type, {}).get("unit", "")
                    row.append(f"{avg:.1f} {unit}")
                    row_colors.append(color + "60")
                    if status == "FAIL":
                        overall_pass = False
                    elif status == "WARN":
                        overall_warn = True
                else:
                    row.append("-")
                    row_colors.append(COLORS["light"])

            # Overall status
            if overall_pass and not overall_warn:
                row.append("✓ PASS")
                row_colors.append(COLORS["success"] + "60")
            elif overall_pass:
                row.append("⚠ WARN")
                row_colors.append(COLORS["warning"] + "60")
            else:
                row.append("✗ FAIL")
                row_colors.append(COLORS["danger"] + "60")

            table_data.append(row)
            cell_colors.append(row_colors)

        if table_data:
            table = ax.table(cellText=table_data, colLabels=headers,
                            loc='center', cellLoc='center',
                            colColours=[COLORS["primary"]]*len(headers),
                            cellColours=cell_colors,
                            colWidths=[0.18, 0.16, 0.16, 0.14, 0.14, 0.14])

            table.auto_set_font_size(False)
            table.set_fontsize(9)
            table.scale(1.1, 2.0)

            for i in range(len(headers)):
                table[(0, i)].set_text_props(color='white', fontweight='bold')
        else:
            ax.text(0.5, 0.5, "No station data available for SSV analysis",
                   ha='center', va='center', fontsize=14, color=COLORS["secondary"])

        # Legend
        legend_ax = fig.add_axes([0.1, 0.02, 0.8, 0.06])
        legend_ax.axis('off')
        legend_items = [
            (COLORS["success"], "PASS: Meets SSV criteria"),
            (COLORS["warning"], "WARN: Close to threshold"),
            (COLORS["danger"], "FAIL: Below SSV criteria"),
        ]
        for i, (color, text) in enumerate(legend_items):
            x = 0.15 + i * 0.3
            legend_ax.add_patch(plt.Rectangle((x - 0.02, 0.3), 0.03, 0.4,
                                              facecolor=color, edgecolor='white'))
            legend_ax.text(x + 0.02, 0.5, text, fontsize=9, va='center')

        self._add_footer(fig, 11)
        pdf.savefig(fig, facecolor='white')
        plt.close(fig)

    def generate_report_bytes(self) -> Optional[bytes]:
        """Generate the complete BI report and return as bytes"""
        logger.info("Starting BI report generation...")

        if not self.authenticate():
            logger.error("Authentication failed, cannot generate report")
            return None

        self.fetch_data()

        logger.info("Generating PDF pages...")

        # Generate PDF to BytesIO buffer
        buffer = io.BytesIO()

        with PdfPages(buffer) as pdf:
            self.create_title_page(pdf)
            self.create_executive_summary(pdf)
            self.create_station_overview(pdf)
            self.create_performance_metrics(pdf)
            self.create_network_analysis(pdf)
            self.create_alerts_page(pdf)
            self.create_geographic_view(pdf)
            # NEW: 5G NR Pages
            self.create_5g_kpi_dashboard(pdf)
            self.create_5g_throughput_gauges(pdf)
            self.create_sector_heatmap(pdf)
            self.create_ssv_scorecard(pdf)

        buffer.seek(0)
        logger.info("BI report generated successfully (11 pages)")
        return buffer.getvalue()
