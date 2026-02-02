package com.huawei.tmf.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * TMF642 Alarm - represents a network alarm event.
 * Based on TM Forum TMF642 Alarm Management API.
 */
@Document(collection = "tmf_alarms")
@CompoundIndex(name = "idx_resource_state", def = "{'alarmedObjectId': 1, 'state': 1}")
public class Alarm {

    @Id
    private String id;

    private String href;

    // External alarm identifier from the source system
    @Indexed
    private String externalAlarmId;

    // Alarm type classification
    @Indexed
    private AlarmType alarmType;  // communicationsAlarm, processingErrorAlarm, etc.

    // Probable cause (X.733 standard)
    private String probableCause;

    // Specific problem - detailed description
    private String specificProblem;

    // Alarm severity
    @Indexed
    private PerceivedSeverity perceivedSeverity;

    // Current state of the alarm
    @Indexed
    private AlarmState state;  // raised, updated, cleared, etc.

    // Is this alarm acknowledged?
    @Indexed
    private Boolean acked;

    // Who acknowledged it
    private String ackedBy;

    // When acknowledged
    private Instant ackTime;

    // System that acknowledged the alarm
    private String ackSystemId;

    // Acknowledgement state (TMF642: unacknowledged, acknowledged)
    private String ackState;

    // User who acknowledged the alarm
    private String ackUserId;

    // User who cleared the alarm
    private String clearUserId;

    // System that cleared the alarm
    private String clearSystemId;

    // Time the alarm was raised
    @Indexed
    private Instant alarmRaisedTime;

    // Time the alarm was last updated
    private Instant alarmReportingTime;

    // Time the alarm was changed (severity, state, etc.)
    private Instant alarmChangedTime;

    // Time the alarm was cleared
    private Instant alarmClearedTime;

    // The resource that raised the alarm
    @Indexed
    private String alarmedObjectId;

    private String alarmedObjectHref;

    private String alarmedObjectType;

    // Root cause flag
    private Boolean isRootCause;

    // Correlated alarms
    private List<AlarmRef> correlatedAlarm;

    // Parent alarms
    private List<AlarmRef> parentAlarm;

    // Source system that reported the alarm
    @Indexed
    private String sourceSystemId;

    // Reporting system
    private String reportingSystemId;

    // Service affecting flag
    private Boolean serviceAffecting;

    // Planned outage indicator
    private Boolean plannedOutageIndicator;

    // Additional text/description
    private String alarmDetails;

    // Proposed repair actions
    private String proposedRepairedActions;

    // Affected services
    private List<AffectedService> affectedService;

    // Comments/notes on the alarm
    private List<Comment> comment;

    // Cross references to external systems
    private List<CrossedThresholdInformation> crossedThresholdInformation;

    // Place where alarm originated
    private List<RelatedPlaceRefOrValue> place;

    // TMF standard fields
    private String baseType;
    private String schemaLocation;
    private String type;

    // Enums
    public enum AlarmType {
        communicationsAlarm,
        processingErrorAlarm,
        environmentalAlarm,
        qualityOfServiceAlarm,
        equipmentAlarm,
        integrityViolation,
        securityServiceOrMechanismViolation,
        timeDomainViolation
    }

    public enum PerceivedSeverity {
        critical,
        major,
        minor,
        warning,
        indeterminate,
        cleared
    }

    public enum AlarmState {
        raised,
        updated,
        cleared,
        acknowledged
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHref() { return href; }
    public void setHref(String href) { this.href = href; }

    public String getExternalAlarmId() { return externalAlarmId; }
    public void setExternalAlarmId(String externalAlarmId) { this.externalAlarmId = externalAlarmId; }

    public AlarmType getAlarmType() { return alarmType; }
    public void setAlarmType(AlarmType alarmType) { this.alarmType = alarmType; }

    public String getProbableCause() { return probableCause; }
    public void setProbableCause(String probableCause) { this.probableCause = probableCause; }

    public String getSpecificProblem() { return specificProblem; }
    public void setSpecificProblem(String specificProblem) { this.specificProblem = specificProblem; }

    public PerceivedSeverity getPerceivedSeverity() { return perceivedSeverity; }
    public void setPerceivedSeverity(PerceivedSeverity perceivedSeverity) { this.perceivedSeverity = perceivedSeverity; }

    public AlarmState getState() { return state; }
    public void setState(AlarmState state) { this.state = state; }

    public Boolean getAcked() { return acked; }
    public void setAcked(Boolean acked) { this.acked = acked; }

    public String getAckedBy() { return ackedBy; }
    public void setAckedBy(String ackedBy) { this.ackedBy = ackedBy; }

    public Instant getAckTime() { return ackTime; }
    public void setAckTime(Instant ackTime) { this.ackTime = ackTime; }

    public String getAckState() { return ackState; }
    public void setAckState(String ackState) { this.ackState = ackState; }

    public String getAckUserId() { return ackUserId; }
    public void setAckUserId(String ackUserId) { this.ackUserId = ackUserId; }

    public String getClearUserId() { return clearUserId; }
    public void setClearUserId(String clearUserId) { this.clearUserId = clearUserId; }

    public String getClearSystemId() { return clearSystemId; }
    public void setClearSystemId(String clearSystemId) { this.clearSystemId = clearSystemId; }

    public String getAckSystemId() { return ackSystemId; }
    public void setAckSystemId(String ackSystemId) { this.ackSystemId = ackSystemId; }

    public Instant getAlarmRaisedTime() { return alarmRaisedTime; }
    public void setAlarmRaisedTime(Instant alarmRaisedTime) { this.alarmRaisedTime = alarmRaisedTime; }

    public Instant getAlarmReportingTime() { return alarmReportingTime; }
    public void setAlarmReportingTime(Instant alarmReportingTime) { this.alarmReportingTime = alarmReportingTime; }

    public Instant getAlarmChangedTime() { return alarmChangedTime; }
    public void setAlarmChangedTime(Instant alarmChangedTime) { this.alarmChangedTime = alarmChangedTime; }

    public Instant getAlarmClearedTime() { return alarmClearedTime; }
    public void setAlarmClearedTime(Instant alarmClearedTime) { this.alarmClearedTime = alarmClearedTime; }

    public String getAlarmedObjectId() { return alarmedObjectId; }
    public void setAlarmedObjectId(String alarmedObjectId) { this.alarmedObjectId = alarmedObjectId; }

    public String getAlarmedObjectHref() { return alarmedObjectHref; }
    public void setAlarmedObjectHref(String alarmedObjectHref) { this.alarmedObjectHref = alarmedObjectHref; }

    public String getAlarmedObjectType() { return alarmedObjectType; }
    public void setAlarmedObjectType(String alarmedObjectType) { this.alarmedObjectType = alarmedObjectType; }

    public Boolean getIsRootCause() { return isRootCause; }
    public void setIsRootCause(Boolean isRootCause) { this.isRootCause = isRootCause; }

    public List<AlarmRef> getCorrelatedAlarm() { return correlatedAlarm; }
    public void setCorrelatedAlarm(List<AlarmRef> correlatedAlarm) { this.correlatedAlarm = correlatedAlarm; }

    public List<AlarmRef> getParentAlarm() { return parentAlarm; }
    public void setParentAlarm(List<AlarmRef> parentAlarm) { this.parentAlarm = parentAlarm; }

    public String getSourceSystemId() { return sourceSystemId; }
    public void setSourceSystemId(String sourceSystemId) { this.sourceSystemId = sourceSystemId; }

    public String getReportingSystemId() { return reportingSystemId; }
    public void setReportingSystemId(String reportingSystemId) { this.reportingSystemId = reportingSystemId; }

    public Boolean getServiceAffecting() { return serviceAffecting; }
    public void setServiceAffecting(Boolean serviceAffecting) { this.serviceAffecting = serviceAffecting; }

    public Boolean getPlannedOutageIndicator() { return plannedOutageIndicator; }
    public void setPlannedOutageIndicator(Boolean plannedOutageIndicator) { this.plannedOutageIndicator = plannedOutageIndicator; }

    public String getAlarmDetails() { return alarmDetails; }
    public void setAlarmDetails(String alarmDetails) { this.alarmDetails = alarmDetails; }

    public String getProposedRepairedActions() { return proposedRepairedActions; }
    public void setProposedRepairedActions(String proposedRepairedActions) { this.proposedRepairedActions = proposedRepairedActions; }

    public List<AffectedService> getAffectedService() { return affectedService; }
    public void setAffectedService(List<AffectedService> affectedService) { this.affectedService = affectedService; }

    public List<Comment> getComment() { return comment; }
    public void setComment(List<Comment> comment) { this.comment = comment; }

    public List<CrossedThresholdInformation> getCrossedThresholdInformation() { return crossedThresholdInformation; }
    public void setCrossedThresholdInformation(List<CrossedThresholdInformation> crossedThresholdInformation) { this.crossedThresholdInformation = crossedThresholdInformation; }

    public List<RelatedPlaceRefOrValue> getPlace() { return place; }
    public void setPlace(List<RelatedPlaceRefOrValue> place) { this.place = place; }

    public String getBaseType() { return baseType; }
    public void setBaseType(String baseType) { this.baseType = baseType; }

    public String getSchemaLocation() { return schemaLocation; }
    public void setSchemaLocation(String schemaLocation) { this.schemaLocation = schemaLocation; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    // Nested classes

    public static class AlarmRef {
        private String id;
        private String href;
        private String name;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getHref() { return href; }
        public void setHref(String href) { this.href = href; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class AffectedService {
        private String id;
        private String href;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getHref() { return href; }
        public void setHref(String href) { this.href = href; }
    }

    public static class Comment {
        private String id;
        private String comment;
        private String systemId;
        private Instant time;
        private String userId;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
        public String getSystemId() { return systemId; }
        public void setSystemId(String systemId) { this.systemId = systemId; }
        public Instant getTime() { return time; }
        public void setTime(Instant time) { this.time = time; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
    }

    public static class CrossedThresholdInformation {
        private String direction;  // "up", "down"
        private String granularity;
        private String indicatorName;
        private String indicatorUnit;
        private String observedValue;
        private String thresholdCrossingDescription;
        private String thresholdId;
        private String thresholdValue;

        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }
        public String getGranularity() { return granularity; }
        public void setGranularity(String granularity) { this.granularity = granularity; }
        public String getIndicatorName() { return indicatorName; }
        public void setIndicatorName(String indicatorName) { this.indicatorName = indicatorName; }
        public String getIndicatorUnit() { return indicatorUnit; }
        public void setIndicatorUnit(String indicatorUnit) { this.indicatorUnit = indicatorUnit; }
        public String getObservedValue() { return observedValue; }
        public void setObservedValue(String observedValue) { this.observedValue = observedValue; }
        public String getThresholdCrossingDescription() { return thresholdCrossingDescription; }
        public void setThresholdCrossingDescription(String thresholdCrossingDescription) { this.thresholdCrossingDescription = thresholdCrossingDescription; }
        public String getThresholdId() { return thresholdId; }
        public void setThresholdId(String thresholdId) { this.thresholdId = thresholdId; }
        public String getThresholdValue() { return thresholdValue; }
        public void setThresholdValue(String thresholdValue) { this.thresholdValue = thresholdValue; }
    }

    public static class RelatedPlaceRefOrValue {
        private String id;
        private String href;
        private String name;
        private String role;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getHref() { return href; }
        public void setHref(String href) { this.href = href; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
}
