package io.github.erselseyit.basestation.station.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * RF Measurement entity for detailed 5G NR radio measurements.
 * Stores per-sector, per-frequency band measurement data.
 * Based on Huawei SSV Test Result structure.
 */
@Entity
@Table(name = "rf_measurements", indexes = {
    @Index(name = "idx_rf_station", columnList = "station_id"),
    @Index(name = "idx_rf_timestamp", columnList = "measurementTime"),
    @Index(name = "idx_rf_band", columnList = "frequencyBand")
})
@EntityListeners(AuditingEntityListener.class)
public class RFMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private BaseStation station;

    @Column(nullable = false)
    private Integer sectorNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FrequencyBand frequencyBand;

    private Integer bandwidth; // MHz

    // Throughput metrics (Mbps)
    private Double dlThroughput;
    private Double ulThroughput;
    private Double pdcpThroughput;
    private Double rlcThroughput;

    // Signal quality metrics
    private Double rsrp; // dBm
    private Double sinr; // dB
    private Double rsrq; // dB

    // Latency
    private Double latency; // ms

    // Radio parameters
    @Enumerated(EnumType.STRING)
    private RankIndicator rankIndicator;

    private Double avgMcs;
    private Integer rbPerSlot;
    private Double initialBler; // %

    // "grant" is a reserved SQL keyword; without an explicit column name
    // Hibernate emits `grant float(53)`, which PostgreSQL rejects with a
    // syntax error and no table is created.
    @Column(name = "ul_grant")
    private Double grant;

    // RF quality
    private Double txImbalance; // dB
    private Double vswr;

    // Cell identity
    private Integer pci;
    private Long gnbCellId;
    private Integer earfcn;

    // Test results
    @Enumerated(EnumType.STRING)
    private TestResult crossConnectionCheck;

    @Enumerated(EnumType.STRING)
    private TestResult antennaDirectionCheck;

    private Double handoverSuccessRate; // %

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime measurementTime;

    @Column(length = 500)
    private String comments;

    // Enums

    /** Radio access technology a band belongs to. */
    public enum RadioTechnology {
        NR, LTE
    }

    /**
     * Operating band an RF measurement was taken on.
     *
     * <p>This is the RF-test-record taxonomy: it spans both NR and LTE bands
     * and is broader than the metric-pipeline {@code Band} enum in
     * monitoring-service (which carries only the NR bands a device actually
     * streams metrics on, plus a {@code NONE} sentinel for band-less metrics
     * such as CPU and temperature). The two are deliberately not merged — they
     * model different things and never exchange values. The common key between
     * them, when a bridge is needed, is the 3GPP band designator exposed by
     * {@link #getBandDesignator()} (e.g. {@code "n28"}).
     *
     * <p>The per-constant data (technology, 3GPP designator, centre frequency)
     * is held in instance fields rather than parsed out of the constant name,
     * per Effective Java Item 34.
     */
    public enum FrequencyBand {
        NR700_N28(RadioTechnology.NR, "n28", 700),
        NR3500_N78(RadioTechnology.NR, "n78", 3500),   // 40 MHz or 100 MHz
        NR2600_N41(RadioTechnology.NR, "n41", 2600),
        LTE_B1(RadioTechnology.LTE, "B1", 2100),
        LTE_B3(RadioTechnology.LTE, "B3", 1800),
        LTE_B7(RadioTechnology.LTE, "B7", 2600),
        LTE_B20(RadioTechnology.LTE, "B20", 800);

        private final RadioTechnology technology;
        private final String bandDesignator;
        private final int centreFrequencyMhz;

        FrequencyBand(RadioTechnology technology, String bandDesignator, int centreFrequencyMhz) {
            this.technology = technology;
            this.bandDesignator = bandDesignator;
            this.centreFrequencyMhz = centreFrequencyMhz;
        }

        /** Radio access technology (NR or LTE). */
        public RadioTechnology getTechnology() {
            return technology;
        }

        /** 3GPP operating-band designator, e.g. {@code "n28"} (NR) or {@code "B1"} (E-UTRA). */
        public String getBandDesignator() {
            return bandDesignator;
        }

        /** Approximate centre frequency in MHz, for display and sorting. */
        public int getCentreFrequencyMhz() {
            return centreFrequencyMhz;
        }
    }

    public enum RankIndicator {
        RANK1, RANK2, RANK4
    }

    public enum TestResult {
        PASS, FAIL, NOT_TESTED
    }

    // Constructors
    public RFMeasurement() {
    }

    public RFMeasurement(BaseStation station, Integer sectorNumber, FrequencyBand frequencyBand) {
        this.station = station;
        this.sectorNumber = sectorNumber;
        this.frequencyBand = frequencyBand;
        this.measurementTime = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BaseStation getStation() {
        return station;
    }

    public void setStation(BaseStation station) {
        this.station = station;
    }

    public Integer getSectorNumber() {
        return sectorNumber;
    }

    public void setSectorNumber(Integer sectorNumber) {
        this.sectorNumber = sectorNumber;
    }

    public FrequencyBand getFrequencyBand() {
        return frequencyBand;
    }

    public void setFrequencyBand(FrequencyBand frequencyBand) {
        this.frequencyBand = frequencyBand;
    }

    public Integer getBandwidth() {
        return bandwidth;
    }

    public void setBandwidth(Integer bandwidth) {
        this.bandwidth = bandwidth;
    }

    public Double getDlThroughput() {
        return dlThroughput;
    }

    public void setDlThroughput(Double dlThroughput) {
        this.dlThroughput = dlThroughput;
    }

    public Double getUlThroughput() {
        return ulThroughput;
    }

    public void setUlThroughput(Double ulThroughput) {
        this.ulThroughput = ulThroughput;
    }

    public Double getPdcpThroughput() {
        return pdcpThroughput;
    }

    public void setPdcpThroughput(Double pdcpThroughput) {
        this.pdcpThroughput = pdcpThroughput;
    }

    public Double getRlcThroughput() {
        return rlcThroughput;
    }

    public void setRlcThroughput(Double rlcThroughput) {
        this.rlcThroughput = rlcThroughput;
    }

    public Double getRsrp() {
        return rsrp;
    }

    public void setRsrp(Double rsrp) {
        this.rsrp = rsrp;
    }

    public Double getSinr() {
        return sinr;
    }

    public void setSinr(Double sinr) {
        this.sinr = sinr;
    }

    public Double getRsrq() {
        return rsrq;
    }

    public void setRsrq(Double rsrq) {
        this.rsrq = rsrq;
    }

    public Double getLatency() {
        return latency;
    }

    public void setLatency(Double latency) {
        this.latency = latency;
    }

    public RankIndicator getRankIndicator() {
        return rankIndicator;
    }

    public void setRankIndicator(RankIndicator rankIndicator) {
        this.rankIndicator = rankIndicator;
    }

    public Double getAvgMcs() {
        return avgMcs;
    }

    public void setAvgMcs(Double avgMcs) {
        this.avgMcs = avgMcs;
    }

    public Integer getRbPerSlot() {
        return rbPerSlot;
    }

    public void setRbPerSlot(Integer rbPerSlot) {
        this.rbPerSlot = rbPerSlot;
    }

    public Double getInitialBler() {
        return initialBler;
    }

    public void setInitialBler(Double initialBler) {
        this.initialBler = initialBler;
    }

    public Double getGrant() {
        return grant;
    }

    public void setGrant(Double grant) {
        this.grant = grant;
    }

    public Double getTxImbalance() {
        return txImbalance;
    }

    public void setTxImbalance(Double txImbalance) {
        this.txImbalance = txImbalance;
    }

    public Double getVswr() {
        return vswr;
    }

    public void setVswr(Double vswr) {
        this.vswr = vswr;
    }

    public Integer getPci() {
        return pci;
    }

    public void setPci(Integer pci) {
        this.pci = pci;
    }

    public Long getGnbCellId() {
        return gnbCellId;
    }

    public void setGnbCellId(Long gnbCellId) {
        this.gnbCellId = gnbCellId;
    }

    public Integer getEarfcn() {
        return earfcn;
    }

    public void setEarfcn(Integer earfcn) {
        this.earfcn = earfcn;
    }

    public TestResult getCrossConnectionCheck() {
        return crossConnectionCheck;
    }

    public void setCrossConnectionCheck(TestResult crossConnectionCheck) {
        this.crossConnectionCheck = crossConnectionCheck;
    }

    public TestResult getAntennaDirectionCheck() {
        return antennaDirectionCheck;
    }

    public void setAntennaDirectionCheck(TestResult antennaDirectionCheck) {
        this.antennaDirectionCheck = antennaDirectionCheck;
    }

    public Double getHandoverSuccessRate() {
        return handoverSuccessRate;
    }

    public void setHandoverSuccessRate(Double handoverSuccessRate) {
        this.handoverSuccessRate = handoverSuccessRate;
    }

    public LocalDateTime getMeasurementTime() {
        return measurementTime;
    }

    public void setMeasurementTime(LocalDateTime measurementTime) {
        this.measurementTime = measurementTime;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }
}
