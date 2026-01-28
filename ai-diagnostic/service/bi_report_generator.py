#!/usr/bin/env python3
"""
BI Report Generator Module

Generates professional PDF reports with charts and analytics.
Used by the AI diagnostic service for on-demand report generation.
"""

import io
import logging
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
            response = requests.post(
                f"{self.api_url}/api/v1/auth/login",
                json={"username": "admin", "password": "adminPassword123!"},
                timeout=10,
            )
            if response.status_code == 200:
                self.token = response.json().get("token")
                logger.info("Authenticated for BI report generation")
                return True
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

    def create_alerts_page(self, pdf: PdfPages):
        """Create alerts and notifications page"""
        fig = plt.figure(figsize=(11, 8.5))
        self._add_header(fig, "Alerts & Notifications", "System Health Monitoring")

        gs = GridSpec(2, 2, figure=fig, left=0.08, right=0.92, top=0.86, bottom=0.1,
                     hspace=0.35, wspace=0.3)

        # Alert types
        ax1 = fig.add_subplot(gs[0, 0])
        alert_types = defaultdict(int)
        for a in self.alerts:
            atype = a.get("type", a.get("alertType", "OTHER"))
            alert_types[atype] += 1

        if alert_types:
            types = list(alert_types.keys())
            counts = list(alert_types.values())
            colors = [COLORS["danger"], COLORS["warning"], COLORS["info"], COLORS["secondary"]]

            bars = ax1.bar(types, counts, color=colors[:len(types)], edgecolor='white')
            for bar, count in zip(bars, counts):
                ax1.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 0.1,
                        str(count), ha='center', fontweight='bold')
            ax1.set_ylabel("Count")
        else:
            ax1.text(0.5, 0.5, "No alerts recorded", ha='center', va='center',
                    transform=ax1.transAxes, fontsize=12, color=COLORS["secondary"])
        ax1.set_title("Alert Types", pad=15)

        # Notification types
        ax2 = fig.add_subplot(gs[0, 1])
        notif_types = defaultdict(int)
        for n in self.notifications:
            ntype = n.get("type", "OTHER")
            notif_types[ntype] += 1

        if notif_types:
            types = list(notif_types.keys())
            counts = list(notif_types.values())
            color_map = {"ALERT": COLORS["danger"], "WARNING": COLORS["warning"], "INFO": COLORS["info"]}
            colors = [color_map.get(t, COLORS["secondary"]) for t in types]

            _, _, autotexts = ax2.pie(counts, labels=types, colors=colors,
                                      autopct='%1.0f%%', startangle=90,
                                      wedgeprops={'width': 0.7, 'edgecolor': 'white'})
            for autotext in autotexts:
                autotext.set_fontweight('bold')
        else:
            ax2.text(0.5, 0.5, "No notifications", ha='center', va='center',
                    transform=ax2.transAxes, fontsize=12, color=COLORS["secondary"])
        ax2.set_title("Notification Distribution", pad=15)

        # Alerts by station
        ax3 = fig.add_subplot(gs[1, 0])
        alerts_by_station = defaultdict(int)
        for a in self.alerts:
            sid = str(a.get("stationId", "?"))
            alerts_by_station[sid] += 1

        if alerts_by_station:
            stations = list(alerts_by_station.keys())[:6]
            counts = [alerts_by_station[s] for s in stations]

            bars = ax3.bar(stations, counts, color=COLORS["danger"], edgecolor='white', alpha=0.8)
            for bar, count in zip(bars, counts):
                ax3.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 0.1,
                        str(count), ha='center', fontweight='bold')
            ax3.set_ylabel("Alert Count")
            ax3.set_xlabel("Station ID")
        else:
            ax3.text(0.5, 0.5, "No alerts by station", ha='center', va='center',
                    transform=ax3.transAxes, fontsize=12, color=COLORS["secondary"])
        ax3.set_title("Alerts by Station", pad=15)

        # Recent alerts table
        ax4 = fig.add_subplot(gs[1, 1])
        ax4.axis('off')

        if self.alerts:
            headers = ["Station", "Type", "Message"]
            table_data = []
            for a in self.alerts[:6]:
                table_data.append([
                    str(a.get("stationId", "-"))[:8],
                    a.get("type", a.get("alertType", "-"))[:10],
                    a.get("message", "-")[:30]
                ])

            table = ax4.table(cellText=table_data, colLabels=headers,
                             loc='center', cellLoc='left',
                             colColours=[COLORS["danger"]]*len(headers),
                             colWidths=[0.18, 0.22, 0.55])
            table.auto_set_font_size(False)
            table.set_fontsize(8)
            table.scale(1.05, 1.5)

            for i in range(len(headers)):
                table[(0, i)].set_text_props(color='white', fontweight='bold')
        else:
            ax4.text(0.5, 0.5, "No recent alerts", ha='center', va='center',
                    transform=ax4.transAxes, fontsize=12, color=COLORS["secondary"])

        ax4.set_title("Recent Alerts", pad=15, loc='center')

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

        buffer.seek(0)
        logger.info("BI report generated successfully")
        return buffer.getvalue()
