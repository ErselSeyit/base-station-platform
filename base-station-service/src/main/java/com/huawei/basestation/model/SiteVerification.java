package com.huawei.basestation.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Site Verification (SVT) tracking entity.
 * Tracks the deployment and verification status of 5G base stations.
 * Based on Huawei SSV Daily Report structure.
 */
@Entity
@Table(name = "site_verifications", indexes = {
    @Index(name = "idx_svt_site_id", columnList = "siteId"),
    @Index(name = "idx_svt_region", columnList = "region"),
    @Index(name = "idx_svt_status", columnList = "svtDoneStatus"),
    @Index(name = "idx_svt_subcon", columnList = "subcontractor")
})
@EntityListeners(AuditingEntityListener.class)
public class SiteVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String siteId;

    private String ossSiteId;

    private String duName;

    private String oss;

    @Column(nullable = false)
    private String region;

    private String city;

    private LocalDate svtReadyDate;

    @Enumerated(EnumType.STRING)
    private Subcontractor subcontractor;

    @Enumerated(EnumType.STRING)
    private ConfigStatus configStatus;

    @Enumerated(EnumType.STRING)
    private TnCheckStatus tnCheckStatus;

    @Enumerated(EnumType.STRING)
    private VisitStatus siteVisitStatus;

    private LocalDate siteVisitDate;

    @Enumerated(EnumType.STRING)
    private SvtStatus svtDoneStatus;

    private LocalDate svtDoneDate;

    @Enumerated(EnumType.STRING)
    private SvtIssueType svtIssue;

    private LocalDate issueDate;

    @Column(length = 500)
    private String issueDetail;

    private Boolean fixed;

    private LocalDate fixedDate;

    private Boolean svtReportSent;

    private LocalDate svtReportSendDate;

    private Boolean svtApproved;

    private LocalDate svtApprovedDate;

    @Enumerated(EnumType.STRING)
    private OnAirStatus onAirStatus;

    private LocalDate onAirDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    private BaseStation station;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Enums
    public enum Subcontractor {
        CENTRO, NOYA, QUALA, TNI
    }

    public enum ConfigStatus {
        OK, NOK, PENDING
    }

    public enum TnCheckStatus {
        OK, NOK, PENDING
    }

    public enum VisitStatus {
        OK, SCHEDULED, PENDING, CANCELLED
    }

    public enum SvtStatus {
        OK, IN_PROGRESS, PENDING, FAILED
    }

    public enum SvtIssueType {
        NONE, CROSS_CONNECTION, INSTALLATION, THROUGHPUT, ALARM, TN, RF_ISSUE, OTHER
    }

    public enum OnAirStatus {
        ON_AIR, PENDING, BLOCKED
    }

    // Constructors
    public SiteVerification() {
    }

    public SiteVerification(String siteId, String region, String city) {
        this.siteId = siteId;
        this.region = region;
        this.city = city;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSiteId() {
        return siteId;
    }

    public void setSiteId(String siteId) {
        this.siteId = siteId;
    }

    public String getOssSiteId() {
        return ossSiteId;
    }

    public void setOssSiteId(String ossSiteId) {
        this.ossSiteId = ossSiteId;
    }

    public String getDuName() {
        return duName;
    }

    public void setDuName(String duName) {
        this.duName = duName;
    }

    public String getOss() {
        return oss;
    }

    public void setOss(String oss) {
        this.oss = oss;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public LocalDate getSvtReadyDate() {
        return svtReadyDate;
    }

    public void setSvtReadyDate(LocalDate svtReadyDate) {
        this.svtReadyDate = svtReadyDate;
    }

    public Subcontractor getSubcontractor() {
        return subcontractor;
    }

    public void setSubcontractor(Subcontractor subcontractor) {
        this.subcontractor = subcontractor;
    }

    public ConfigStatus getConfigStatus() {
        return configStatus;
    }

    public void setConfigStatus(ConfigStatus configStatus) {
        this.configStatus = configStatus;
    }

    public TnCheckStatus getTnCheckStatus() {
        return tnCheckStatus;
    }

    public void setTnCheckStatus(TnCheckStatus tnCheckStatus) {
        this.tnCheckStatus = tnCheckStatus;
    }

    public VisitStatus getSiteVisitStatus() {
        return siteVisitStatus;
    }

    public void setSiteVisitStatus(VisitStatus siteVisitStatus) {
        this.siteVisitStatus = siteVisitStatus;
    }

    public LocalDate getSiteVisitDate() {
        return siteVisitDate;
    }

    public void setSiteVisitDate(LocalDate siteVisitDate) {
        this.siteVisitDate = siteVisitDate;
    }

    public SvtStatus getSvtDoneStatus() {
        return svtDoneStatus;
    }

    public void setSvtDoneStatus(SvtStatus svtDoneStatus) {
        this.svtDoneStatus = svtDoneStatus;
    }

    public LocalDate getSvtDoneDate() {
        return svtDoneDate;
    }

    public void setSvtDoneDate(LocalDate svtDoneDate) {
        this.svtDoneDate = svtDoneDate;
    }

    public SvtIssueType getSvtIssue() {
        return svtIssue;
    }

    public void setSvtIssue(SvtIssueType svtIssue) {
        this.svtIssue = svtIssue;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public String getIssueDetail() {
        return issueDetail;
    }

    public void setIssueDetail(String issueDetail) {
        this.issueDetail = issueDetail;
    }

    public Boolean getFixed() {
        return fixed;
    }

    public void setFixed(Boolean fixed) {
        this.fixed = fixed;
    }

    public LocalDate getFixedDate() {
        return fixedDate;
    }

    public void setFixedDate(LocalDate fixedDate) {
        this.fixedDate = fixedDate;
    }

    public Boolean getSvtReportSent() {
        return svtReportSent;
    }

    public void setSvtReportSent(Boolean svtReportSent) {
        this.svtReportSent = svtReportSent;
    }

    public LocalDate getSvtReportSendDate() {
        return svtReportSendDate;
    }

    public void setSvtReportSendDate(LocalDate svtReportSendDate) {
        this.svtReportSendDate = svtReportSendDate;
    }

    public Boolean getSvtApproved() {
        return svtApproved;
    }

    public void setSvtApproved(Boolean svtApproved) {
        this.svtApproved = svtApproved;
    }

    public LocalDate getSvtApprovedDate() {
        return svtApprovedDate;
    }

    public void setSvtApprovedDate(LocalDate svtApprovedDate) {
        this.svtApprovedDate = svtApprovedDate;
    }

    public OnAirStatus getOnAirStatus() {
        return onAirStatus;
    }

    public void setOnAirStatus(OnAirStatus onAirStatus) {
        this.onAirStatus = onAirStatus;
    }

    public LocalDate getOnAirDate() {
        return onAirDate;
    }

    public void setOnAirDate(LocalDate onAirDate) {
        this.onAirDate = onAirDate;
    }

    public BaseStation getStation() {
        return station;
    }

    public void setStation(BaseStation station) {
        this.station = station;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
