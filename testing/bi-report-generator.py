#!/usr/bin/env python3
"""
BI Report Generator for Base Station Platform
==============================================
Generates beautiful, professional PDF reports with charts and analytics.

Usage:
    python3 testing/bi-report-generator.py [options]
"""

import requests
import argparse
from datetime import datetime
from typing import Dict, List, Any, Optional
import matplotlib.pyplot as plt
from matplotlib.backends.backend_pdf import PdfPages
import matplotlib.patches as mpatches
from matplotlib.gridspec import GridSpec
import numpy as np
from collections import defaultdict

# Professional color palette
COLORS = {
    "primary": "#1a73e8",      # Google Blue
    "secondary": "#5f6368",    # Gray
    "success": "#34a853",      # Green
    "warning": "#fbbc04",      # Yellow
    "danger": "#ea4335",       # Red
    "info": "#4285f4",         # Light Blue
    "dark": "#202124",         # Dark Gray
    "light": "#f8f9fa",        # Light Gray
    "white": "#ffffff",
    "accent1": "#8ab4f8",      # Light Blue
    "accent2": "#81c995",      # Light Green
    "accent3": "#fdd663",      # Light Yellow
    "accent4": "#f28b82",      # Light Red
    "gradient1": "#667eea",    # Purple
    "gradient2": "#764ba2",    # Deep Purple
}

# Status colors
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

    def __init__(self, api_url: str, output_file: str):
        self.api_url = api_url.rstrip("/")
        self.output_file = output_file
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
                print("[OK] Authenticated")
                return True
            return False
        except Exception as e:
            print(f"[ERROR] Auth failed: {e}")
            return False

    def get_headers(self) -> Dict[str, str]:
        return {"Authorization": f"Bearer {self.token}"} if self.token else {}

    def fetch_data(self):
        """Fetch all data from APIs"""
        print("[...] Fetching data...")

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
                    print(f"  {name}: {len(result)} records")
            except Exception as e:
                print(f"  {name}: failed - {e}")

    def _add_header(self, fig, title: str, subtitle: str = ""):
        """Add professional header to page"""
        # Header background
        header_ax = fig.add_axes([0, 0.92, 1, 0.08])
        header_ax.set_xlim(0, 1)
        header_ax.set_ylim(0, 1)
        header_ax.axis('off')

        # Gradient effect
        gradient = np.linspace(0, 1, 100).reshape(1, -1)
        header_ax.imshow(gradient, extent=[0, 1, 0, 1], aspect='auto',
                        cmap=plt.cm.Blues, alpha=0.3)

        # Title
        header_ax.text(0.03, 0.6, title, fontsize=20, fontweight='bold',
                      color=COLORS["dark"], va='center')
        if subtitle:
            header_ax.text(0.03, 0.2, subtitle, fontsize=11,
                          color=COLORS["secondary"], va='center')

        # Logo placeholder / brand
        header_ax.text(0.97, 0.5, "BASE STATION PLATFORM", fontsize=9,
                      fontweight='bold', color=COLORS["primary"],
                      ha='right', va='center', alpha=0.7)

    def _add_footer(self, fig, page_num: int):
        """Add footer with page number and timestamp"""
        footer_ax = fig.add_axes([0, 0, 1, 0.03])
        footer_ax.set_xlim(0, 1)
        footer_ax.set_ylim(0, 1)
        footer_ax.axis('off')

        # Divider line
        footer_ax.axhline(y=0.9, color=COLORS["secondary"], linewidth=0.5, alpha=0.3)

        # Footer text
        footer_ax.text(0.03, 0.4, f"Generated: {self.report_time.strftime('%Y-%m-%d %H:%M:%S')}",
                      fontsize=8, color=COLORS["secondary"])
        footer_ax.text(0.97, 0.4, f"Page {page_num}",
                      fontsize=8, color=COLORS["secondary"], ha='right')
        footer_ax.text(0.5, 0.4, "Confidential - Internal Use Only",
                      fontsize=8, color=COLORS["secondary"], ha='center', style='italic')

    def create_title_page(self, pdf: PdfPages):
        """Create beautiful title page"""
        fig = plt.figure(figsize=(11, 8.5))

        # Background gradient
        ax = fig.add_axes([0, 0, 1, 1])
        ax.axis('off')

        # Create gradient background
        y = np.linspace(0, 1, 100)
        gradient = y.reshape(-1, 1)
        ax.imshow(gradient, extent=[0, 1, 0, 1], aspect='auto',
                 cmap='Blues', alpha=0.15, origin='lower')

        # Main title
        ax.text(0.5, 0.65, "Base Station Platform", fontsize=36, fontweight='bold',
               ha='center', va='center', color=COLORS["dark"])
        ax.text(0.5, 0.55, "Business Intelligence Report", fontsize=24,
               ha='center', va='center', color=COLORS["primary"])

        # Decorative line
        ax.axhline(y=0.48, xmin=0.3, xmax=0.7, color=COLORS["primary"], linewidth=2)

        # Report info box
        info_box = plt.Rectangle((0.25, 0.2), 0.5, 0.2, fill=True,
                                  facecolor=COLORS["light"], edgecolor=COLORS["secondary"],
                                  linewidth=1, alpha=0.8)
        ax.add_patch(info_box)

        # Stats in box
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

        # Generated timestamp
        ax.text(0.5, 0.1, f"Report Generated: {self.report_time.strftime('%B %d, %Y at %H:%M')}",
               fontsize=11, ha='center', color=COLORS["secondary"])

        pdf.savefig(fig, facecolor='white')
        plt.close(fig)

    def create_executive_summary(self, pdf: PdfPages):
        """Create executive summary page"""
        fig = plt.figure(figsize=(11, 8.5))
        self._add_header(fig, "Executive Summary", "Key Performance Indicators")

        # Main content area
        gs = GridSpec(2, 3, figure=fig, left=0.06, right=0.94, top=0.88, bottom=0.08,
                     hspace=0.3, wspace=0.25)

        # KPI Cards
        kpis = self._calculate_kpis()

        kpi_data = [
            ("System Health", f"{kpis['health_score']:.0f}%", COLORS["success"] if kpis['health_score'] >= 90 else COLORS["warning"]),
            ("Avg CPU Usage", f"{kpis['avg_cpu']:.1f}%", COLORS["success"] if kpis['avg_cpu'] < 70 else COLORS["warning"]),
            ("Avg Temperature", f"{kpis['avg_temp']:.1f}°C", COLORS["success"] if kpis['avg_temp'] < 75 else COLORS["warning"]),
            ("Avg Signal", f"{kpis['avg_signal']:.0f} dBm", COLORS["success"] if kpis['avg_signal'] > -80 else COLORS["warning"]),
            ("Total Throughput", f"{kpis['total_throughput']:.0f} Mbps", COLORS["info"]),
            ("Active Alerts", str(len(self.alerts)), COLORS["danger"] if len(self.alerts) > 5 else COLORS["success"]),
        ]

        for i, (label, value, color) in enumerate(kpi_data):
            ax = fig.add_subplot(gs[i // 3, i % 3])
            ax.axis('off')

            # Card background
            card = plt.Rectangle((0.05, 0.1), 0.9, 0.8, fill=True,
                                 facecolor=COLORS["white"], edgecolor=color,
                                 linewidth=2, alpha=1, transform=ax.transAxes)
            ax.add_patch(card)

            # Value
            ax.text(0.5, 0.55, value, fontsize=28, fontweight='bold',
                   color=color, ha='center', va='center', transform=ax.transAxes)
            # Label
            ax.text(0.5, 0.25, label, fontsize=11, color=COLORS["secondary"],
                   ha='center', va='center', transform=ax.transAxes)

        self._add_footer(fig, 2)
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

    def create_station_overview(self, pdf: PdfPages):
        """Create station overview with beautiful charts"""
        fig = plt.figure(figsize=(11, 8.5))
        self._add_header(fig, "Station Overview", "Status and Distribution Analysis")

        gs = GridSpec(2, 2, figure=fig, left=0.08, right=0.92, top=0.86, bottom=0.1,
                     hspace=0.35, wspace=0.3)

        # 1. Status Pie Chart (Donut style)
        ax1 = fig.add_subplot(gs[0, 0])
        status_counts = defaultdict(int)
        for s in self.stations:
            status_counts[s.get("status", "UNKNOWN")] += 1

        if status_counts:
            labels = list(status_counts.keys())
            sizes = list(status_counts.values())
            colors = [STATUS_COLORS.get(s, COLORS["secondary"]) for s in labels]

            wedges, texts, autotexts = ax1.pie(sizes, labels=labels, colors=colors,
                                               autopct='%1.1f%%', startangle=90,
                                               wedgeprops=dict(width=0.6, edgecolor='white'),
                                               textprops=dict(color=COLORS["dark"]))
            for autotext in autotexts:
                autotext.set_fontweight('bold')
                autotext.set_fontsize(10)

            # Center circle for donut effect
            centre_circle = plt.Circle((0, 0), 0.35, fc='white')
            ax1.add_patch(centre_circle)
            ax1.text(0, 0, f"{len(self.stations)}\nStations", ha='center', va='center',
                    fontsize=14, fontweight='bold', color=COLORS["dark"])
        ax1.set_title("Station Status", pad=15)

        # 2. Station Type Bar Chart
        ax2 = fig.add_subplot(gs[0, 1])
        type_counts = defaultdict(int)
        for s in self.stations:
            type_counts[s.get("stationType", "UNKNOWN")] += 1

        if type_counts:
            types = list(type_counts.keys())
            counts = list(type_counts.values())
            bar_colors = [COLORS["primary"], COLORS["info"], COLORS["accent1"], COLORS["accent2"]]

            bars = ax2.bar(types, counts, color=bar_colors[:len(types)], edgecolor='white', linewidth=1)

            # Add value labels on bars
            for bar, count in zip(bars, counts):
                ax2.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 0.1,
                        str(count), ha='center', va='bottom', fontweight='bold',
                        fontsize=11, color=COLORS["dark"])

            ax2.set_ylabel("Count")
            ax2.set_ylim(0, max(counts) * 1.2)
        ax2.set_title("Station Types", pad=15)

        # 3. Station Table
        ax3 = fig.add_subplot(gs[1, :])
        ax3.axis('off')
        ax3.set_title("Station Details", pad=15, loc='left')

        if self.stations:
            # Prepare table data
            headers = ["ID", "Name", "Location", "Type", "Status", "Power (kW)"]
            table_data = []
            for s in self.stations[:8]:  # Limit rows
                table_data.append([
                    str(s.get("id", "-")),
                    s.get("stationName", "-")[:25],
                    s.get("location", "-")[:20],
                    s.get("stationType", "-"),
                    s.get("status", "-"),
                    f"{s.get('powerConsumption', 0):.1f}" if s.get('powerConsumption') else "-"
                ])

            table = ax3.table(cellText=table_data, colLabels=headers,
                             loc='center', cellLoc='center',
                             colColours=[COLORS["primary"]]*len(headers))

            table.auto_set_font_size(False)
            table.set_fontsize(9)
            table.scale(1, 1.8)

            # Style header
            for i in range(len(headers)):
                table[(0, i)].set_text_props(color='white', fontweight='bold')
                table[(0, i)].set_facecolor(COLORS["primary"])

            # Style status cells with colors
            for row_idx, row in enumerate(table_data):
                status = row[4]
                table[(row_idx + 1, 4)].set_facecolor(
                    STATUS_COLORS.get(status, COLORS["light"]) + "40"  # Add alpha
                )

        self._add_footer(fig, 3)
        pdf.savefig(fig, facecolor='white')
        plt.close(fig)

    def create_performance_metrics(self, pdf: PdfPages):
        """Create performance metrics page"""
        fig = plt.figure(figsize=(11, 8.5))
        self._add_header(fig, "Performance Metrics", "Resource Utilization Analysis")

        gs = GridSpec(2, 2, figure=fig, left=0.08, right=0.92, top=0.86, bottom=0.1,
                     hspace=0.35, wspace=0.3)

        # Group metrics
        metrics_by_station: Dict[str, Dict[str, List[float]]] = defaultdict(lambda: defaultdict(list))
        for m in self.metrics:
            sid = str(m.get("stationId", "?"))
            mtype = m.get("metricType", "")
            value = m.get("value")
            if value is not None:
                metrics_by_station[sid][mtype].append(float(value))

        # 1. CPU Usage Gauge-style bars
        ax1 = fig.add_subplot(gs[0, 0])
        cpu_data = {k: np.mean(v.get("CPU_USAGE", [0])) for k, v in metrics_by_station.items() if v.get("CPU_USAGE")}

        if cpu_data:
            stations = list(cpu_data.keys())[:6]
            values = [cpu_data[s] for s in stations]
            colors = [COLORS["success"] if v < 60 else COLORS["warning"] if v < 80 else COLORS["danger"] for v in values]

            bars = ax1.barh(stations, values, color=colors, edgecolor='white', height=0.6)

            # Add threshold lines
            ax1.axvline(x=60, color=COLORS["warning"], linestyle='--', alpha=0.5, label='Warning (60%)')
            ax1.axvline(x=80, color=COLORS["danger"], linestyle='--', alpha=0.5, label='Critical (80%)')

            for bar, val in zip(bars, values):
                ax1.text(val + 1, bar.get_y() + bar.get_height()/2, f'{val:.1f}%',
                        va='center', fontsize=9, fontweight='bold')

            ax1.set_xlim(0, 100)
            ax1.set_xlabel("Usage (%)")
            ax1.legend(loc='lower right', fontsize=8)
        ax1.set_title("CPU Usage by Station", pad=15)

        # 2. Memory Usage
        ax2 = fig.add_subplot(gs[0, 1])
        mem_data = {k: np.mean(v.get("MEMORY_USAGE", [0])) for k, v in metrics_by_station.items() if v.get("MEMORY_USAGE")}

        if mem_data:
            stations = list(mem_data.keys())[:6]
            values = [mem_data[s] for s in stations]
            colors = [COLORS["success"] if v < 70 else COLORS["warning"] if v < 85 else COLORS["danger"] for v in values]

            bars = ax2.barh(stations, values, color=colors, edgecolor='white', height=0.6)

            ax2.axvline(x=70, color=COLORS["warning"], linestyle='--', alpha=0.5)
            ax2.axvline(x=85, color=COLORS["danger"], linestyle='--', alpha=0.5)

            for bar, val in zip(bars, values):
                ax2.text(val + 1, bar.get_y() + bar.get_height()/2, f'{val:.1f}%',
                        va='center', fontsize=9, fontweight='bold')

            ax2.set_xlim(0, 100)
            ax2.set_xlabel("Usage (%)")
        ax2.set_title("Memory Usage by Station", pad=15)

        # 3. Temperature Distribution
        ax3 = fig.add_subplot(gs[1, 0])
        all_temps = []
        for v in metrics_by_station.values():
            all_temps.extend(v.get("TEMPERATURE", []))

        if all_temps:
            n, bins, patches = ax3.hist(all_temps, bins=15, edgecolor='white', linewidth=1)

            # Color by temperature range
            for i, patch in enumerate(patches):
                temp = (bins[i] + bins[i+1]) / 2
                if temp < 60:
                    patch.set_facecolor(COLORS["success"])
                elif temp < 75:
                    patch.set_facecolor(COLORS["warning"])
                else:
                    patch.set_facecolor(COLORS["danger"])

            mean_temp = np.mean(all_temps)
            ax3.axvline(x=mean_temp, color=COLORS["dark"], linestyle='-', linewidth=2,
                       label=f'Mean: {mean_temp:.1f}°C')
            ax3.axvline(x=80, color=COLORS["danger"], linestyle='--', alpha=0.7,
                       label='Critical: 80°C')

            ax3.set_xlabel("Temperature (°C)")
            ax3.set_ylabel("Frequency")
            ax3.legend(loc='upper right', fontsize=8)
        ax3.set_title("Temperature Distribution", pad=15)

        # 4. Signal Strength
        ax4 = fig.add_subplot(gs[1, 1])
        all_signals = []
        for v in metrics_by_station.values():
            all_signals.extend(v.get("SIGNAL_STRENGTH", []))

        if all_signals:
            n, bins, patches = ax4.hist(all_signals, bins=15, edgecolor='white', linewidth=1)

            for i, patch in enumerate(patches):
                sig = (bins[i] + bins[i+1]) / 2
                if sig > -70:
                    patch.set_facecolor(COLORS["success"])
                elif sig > -90:
                    patch.set_facecolor(COLORS["warning"])
                else:
                    patch.set_facecolor(COLORS["danger"])

            mean_sig = np.mean(all_signals)
            ax4.axvline(x=mean_sig, color=COLORS["dark"], linestyle='-', linewidth=2,
                       label=f'Mean: {mean_sig:.1f} dBm')

            ax4.set_xlabel("Signal Strength (dBm)")
            ax4.set_ylabel("Frequency")
            ax4.legend(loc='upper left', fontsize=8)
        ax4.set_title("Signal Strength Distribution", pad=15)

        self._add_footer(fig, 4)
        pdf.savefig(fig, facecolor='white')
        plt.close(fig)

    def create_network_analysis(self, pdf: PdfPages):
        """Create network performance page"""
        fig = plt.figure(figsize=(11, 8.5))
        self._add_header(fig, "Network Analysis", "Throughput and Connectivity")

        gs = GridSpec(2, 2, figure=fig, left=0.08, right=0.92, top=0.86, bottom=0.1,
                     hspace=0.35, wspace=0.3)

        # Group metrics
        metrics_by_station: Dict[str, Dict[str, List[float]]] = defaultdict(lambda: defaultdict(list))
        for m in self.metrics:
            sid = str(m.get("stationId", "?"))
            mtype = m.get("metricType", "")
            value = m.get("value")
            if value is not None:
                metrics_by_station[sid][mtype].append(float(value))

        # 1. Throughput comparison
        ax1 = fig.add_subplot(gs[0, 0])
        throughput = {k: np.mean(v.get("DATA_THROUGHPUT", [0])) for k, v in metrics_by_station.items() if v.get("DATA_THROUGHPUT")}

        if throughput:
            stations = list(throughput.keys())[:6]
            values = [throughput[s] for s in stations]

            bars = ax1.bar(stations, values, color=COLORS["info"], edgecolor='white', width=0.6)

            for bar, val in zip(bars, values):
                ax1.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 2,
                        f'{val:.0f}', ha='center', fontsize=9, fontweight='bold')

            ax1.set_ylabel("Throughput (Mbps)")
            ax1.set_ylim(0, max(values) * 1.2 if values else 100)
        ax1.set_title("Data Throughput by Station", pad=15)

        # 2. Connection counts
        ax2 = fig.add_subplot(gs[0, 1])
        connections = {k: np.mean(v.get("CONNECTION_COUNT", [0])) for k, v in metrics_by_station.items() if v.get("CONNECTION_COUNT")}

        if connections:
            stations = list(connections.keys())[:6]
            values = [connections[s] for s in stations]

            bars = ax2.bar(stations, values, color=COLORS["accent2"], edgecolor='white', width=0.6)

            for bar, val in zip(bars, values):
                ax2.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 1,
                        f'{val:.0f}', ha='center', fontsize=9, fontweight='bold')

            ax2.set_ylabel("Connections")
            ax2.set_ylim(0, max(values) * 1.2 if values else 100)
        ax2.set_title("Connected Devices by Station", pad=15)

        # 3. Uptime comparison
        ax3 = fig.add_subplot(gs[1, 0])
        uptime = {k: np.mean(v.get("UPTIME", [99])) for k, v in metrics_by_station.items() if v.get("UPTIME")}

        if uptime:
            stations = list(uptime.keys())[:6]
            values = [min(uptime[s], 100) for s in stations]
            colors = [COLORS["success"] if v >= 99 else COLORS["warning"] if v >= 95 else COLORS["danger"] for v in values]

            bars = ax3.bar(stations, values, color=colors, edgecolor='white', width=0.6)

            for bar, val in zip(bars, values):
                ax3.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 0.5,
                        f'{val:.1f}%', ha='center', fontsize=9, fontweight='bold')

            ax3.set_ylabel("Uptime (%)")
            ax3.set_ylim(90, 101)
            ax3.axhline(y=99, color=COLORS["success"], linestyle='--', alpha=0.5, label='Target: 99%')
            ax3.legend(loc='lower right', fontsize=8)
        ax3.set_title("Station Uptime", pad=15)

        # 4. Metrics summary table
        ax4 = fig.add_subplot(gs[1, 1])
        ax4.axis('off')

        metrics_summary = defaultdict(list)
        for m in self.metrics:
            mtype = m.get("metricType", "")
            value = m.get("value")
            if value is not None:
                metrics_summary[mtype].append(float(value))

        if metrics_summary:
            headers = ["Metric", "Count", "Min", "Max", "Avg"]
            table_data = []
            for mtype in sorted(metrics_summary.keys()):
                vals = metrics_summary[mtype]
                table_data.append([
                    mtype[:18],
                    len(vals),
                    f"{min(vals):.1f}",
                    f"{max(vals):.1f}",
                    f"{np.mean(vals):.1f}"
                ])

            table = ax4.table(cellText=table_data[:8], colLabels=headers,
                             loc='center', cellLoc='center',
                             colColours=[COLORS["primary"]]*len(headers))
            table.auto_set_font_size(False)
            table.set_fontsize(9)
            table.scale(1.1, 1.6)

            for i in range(len(headers)):
                table[(0, i)].set_text_props(color='white', fontweight='bold')

        ax4.set_title("Metrics Summary", pad=15, loc='center')

        self._add_footer(fig, 5)
        pdf.savefig(fig, facecolor='white')
        plt.close(fig)

    def create_alerts_page(self, pdf: PdfPages):
        """Create alerts and notifications page"""
        fig = plt.figure(figsize=(11, 8.5))
        self._add_header(fig, "Alerts & Notifications", "System Health Monitoring")

        gs = GridSpec(2, 2, figure=fig, left=0.08, right=0.92, top=0.86, bottom=0.1,
                     hspace=0.35, wspace=0.3)

        # 1. Alert types
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

        # 2. Notification types
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

            wedges, texts, autotexts = ax2.pie(counts, labels=types, colors=colors,
                                               autopct='%1.0f%%', startangle=90,
                                               wedgeprops=dict(width=0.7, edgecolor='white'))
            for autotext in autotexts:
                autotext.set_fontweight('bold')
        else:
            ax2.text(0.5, 0.5, "No notifications", ha='center', va='center',
                    transform=ax2.transAxes, fontsize=12, color=COLORS["secondary"])
        ax2.set_title("Notification Distribution", pad=15)

        # 3. Alerts by station
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

        # 4. Recent alerts table
        ax4 = fig.add_subplot(gs[1, 1])
        ax4.axis('off')

        if self.alerts:
            headers = ["Station", "Type", "Message"]
            table_data = []
            for a in self.alerts[:6]:
                table_data.append([
                    str(a.get("stationId", "-")),
                    a.get("type", a.get("alertType", "-"))[:12],
                    a.get("message", "-")[:35]
                ])

            table = ax4.table(cellText=table_data, colLabels=headers,
                             loc='center', cellLoc='left',
                             colColours=[COLORS["danger"]]*len(headers),
                             colWidths=[0.15, 0.2, 0.65])
            table.auto_set_font_size(False)
            table.set_fontsize(8)
            table.scale(1, 1.5)

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

            scatter = ax.scatter(lons, lats, c=colors, s=300, alpha=0.8,
                               edgecolors='white', linewidths=2, zorder=5)

            # Add station labels
            for i, name in enumerate(names):
                ax.annotate(name, (lons[i], lats[i]),
                           xytext=(8, 8), textcoords='offset points',
                           fontsize=9, fontweight='bold',
                           bbox=dict(boxstyle='round,pad=0.3', facecolor='white', alpha=0.8))

            # Style
            ax.set_xlabel("Longitude", fontsize=11)
            ax.set_ylabel("Latitude", fontsize=11)
            ax.grid(True, alpha=0.3, linestyle='--')

            # Legend
            patches = [mpatches.Patch(color=c, label=s) for s, c in STATUS_COLORS.items()]
            ax.legend(handles=patches, loc='upper right', framealpha=0.9)

            # Add padding
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

    def generate_report(self) -> bool:
        """Generate the complete BI report"""
        print("\n" + "="*50)
        print("  BI REPORT GENERATOR")
        print("="*50)

        if not self.authenticate():
            return False

        self.fetch_data()

        print("\n[...] Generating PDF report...")

        with PdfPages(self.output_file) as pdf:
            print("  Creating title page...")
            self.create_title_page(pdf)

            print("  Creating executive summary...")
            self.create_executive_summary(pdf)

            print("  Creating station overview...")
            self.create_station_overview(pdf)

            print("  Creating performance metrics...")
            self.create_performance_metrics(pdf)

            print("  Creating network analysis...")
            self.create_network_analysis(pdf)

            print("  Creating alerts page...")
            self.create_alerts_page(pdf)

            print("  Creating geographic view...")
            self.create_geographic_view(pdf)

        print(f"\n[OK] Report saved: {self.output_file}")
        print("="*50 + "\n")
        return True


def main():
    parser = argparse.ArgumentParser(description="BI Report Generator")
    parser.add_argument("--api-url", default="http://localhost:8080", help="API Gateway URL")
    parser.add_argument("--output", default=None, help="Output PDF file")

    args = parser.parse_args()

    if args.output is None:
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        args.output = f"bi-report-{timestamp}.pdf"

    generator = BIReportGenerator(args.api_url, args.output)
    generator.generate_report()


if __name__ == "__main__":
    main()
