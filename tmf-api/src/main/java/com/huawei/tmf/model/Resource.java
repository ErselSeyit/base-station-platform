package com.huawei.tmf.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.util.List;

/**
 * TMF639 Resource - represents a physical or logical network resource.
 * Based on TM Forum TMF639 Resource Inventory Management API.
 */
@Document(collection = "tmf_resources")
public class Resource {

    @Id
    private String id;

    private String href;

    @Indexed
    private String name;

    private String description;

    @Indexed
    private String category;  // "BaseStation", "Antenna", "Radio", "Power", etc.

    @Indexed
    private String resourceType;  // "5G_NR", "LTE", "MACRO", "SMALL_CELL"

    @Indexed
    private String operationalState;  // "enable", "disable"

    @Indexed
    private String administrativeState;  // "locked", "unlocked", "shutdown"

    @Indexed
    private String usageState;  // "idle", "active", "busy"

    private String resourceStatus;  // "standby", "alarm", "available", "reserved", "unknown", "suspended"

    private Instant startOperatingDate;

    private Instant endOperatingDate;

    private String resourceVersion;

    // Resource characteristics (dynamic key-value pairs)
    private List<ResourceCharacteristic> resourceCharacteristic;

    // Related resources (parent/child relationships)
    private List<ResourceRelationship> resourceRelationship;

    // Physical location
    private Place place;

    // Related party (owner, operator)
    private List<RelatedParty> relatedParty;

    // Notes/comments
    private List<Note> note;

    // Activation features
    private List<Feature> activationFeature;

    // Attachments (documents, images)
    private List<AttachmentRefOrValue> attachment;

    // Resource specification reference
    private ResourceSpecificationRef resourceSpecification;

    // Lifecycle timestamps
    private Instant createdAt;
    private Instant updatedAt;
    private Instant modifiedAt;

    // Lifecycle state (TMF639)
    private String lifecycleState;

    // External system references
    @Indexed
    private String externalId;  // ID in external system (e.g., station_id)

    // TMF standard fields
    private String baseType;  // "@baseType"
    private String schemaLocation;  // "@schemaLocation"
    private String type;  // "@type"

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHref() { return href; }
    public void setHref(String href) { this.href = href; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getOperationalState() { return operationalState; }
    public void setOperationalState(String operationalState) { this.operationalState = operationalState; }

    public String getAdministrativeState() { return administrativeState; }
    public void setAdministrativeState(String administrativeState) { this.administrativeState = administrativeState; }

    public String getUsageState() { return usageState; }
    public void setUsageState(String usageState) { this.usageState = usageState; }

    public String getResourceStatus() { return resourceStatus; }
    public void setResourceStatus(String resourceStatus) { this.resourceStatus = resourceStatus; }

    public Instant getStartOperatingDate() { return startOperatingDate; }
    public void setStartOperatingDate(Instant startOperatingDate) { this.startOperatingDate = startOperatingDate; }

    public Instant getEndOperatingDate() { return endOperatingDate; }
    public void setEndOperatingDate(Instant endOperatingDate) { this.endOperatingDate = endOperatingDate; }

    public String getResourceVersion() { return resourceVersion; }
    public void setResourceVersion(String resourceVersion) { this.resourceVersion = resourceVersion; }

    public List<ResourceCharacteristic> getResourceCharacteristic() { return resourceCharacteristic; }
    public void setResourceCharacteristic(List<ResourceCharacteristic> resourceCharacteristic) { this.resourceCharacteristic = resourceCharacteristic; }

    public List<ResourceRelationship> getResourceRelationship() { return resourceRelationship; }
    public void setResourceRelationship(List<ResourceRelationship> resourceRelationship) { this.resourceRelationship = resourceRelationship; }

    public Place getPlace() { return place; }
    public void setPlace(Place place) { this.place = place; }

    public List<RelatedParty> getRelatedParty() { return relatedParty; }
    public void setRelatedParty(List<RelatedParty> relatedParty) { this.relatedParty = relatedParty; }

    public List<Note> getNote() { return note; }
    public void setNote(List<Note> note) { this.note = note; }

    public List<Feature> getActivationFeature() { return activationFeature; }
    public void setActivationFeature(List<Feature> activationFeature) { this.activationFeature = activationFeature; }

    public List<AttachmentRefOrValue> getAttachment() { return attachment; }
    public void setAttachment(List<AttachmentRefOrValue> attachment) { this.attachment = attachment; }

    public ResourceSpecificationRef getResourceSpecification() { return resourceSpecification; }
    public void setResourceSpecification(ResourceSpecificationRef resourceSpecification) { this.resourceSpecification = resourceSpecification; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(Instant modifiedAt) { this.modifiedAt = modifiedAt; }

    public String getLifecycleState() { return lifecycleState; }
    public void setLifecycleState(String lifecycleState) { this.lifecycleState = lifecycleState; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getBaseType() { return baseType; }
    public void setBaseType(String baseType) { this.baseType = baseType; }

    public String getSchemaLocation() { return schemaLocation; }
    public void setSchemaLocation(String schemaLocation) { this.schemaLocation = schemaLocation; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    // Nested classes for TMF standard types

    public static class ResourceCharacteristic {
        private String id;
        private String name;
        private String valueType;
        private Object value;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getValueType() { return valueType; }
        public void setValueType(String valueType) { this.valueType = valueType; }
        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
    }

    public static class ResourceRelationship {
        private String id;
        private String href;
        private String relationshipType;  // "contains", "isContainedIn", "supports", etc.
        private ResourceRef resource;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getHref() { return href; }
        public void setHref(String href) { this.href = href; }
        public String getRelationshipType() { return relationshipType; }
        public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }
        public ResourceRef getResource() { return resource; }
        public void setResource(ResourceRef resource) { this.resource = resource; }
    }

    public static class ResourceRef {
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

    public static class Place {
        private String id;
        private String href;
        private String name;
        private String role;  // "installationSite", "deliveryAddress"
        private GeographicLocation geographicLocation;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getHref() { return href; }
        public void setHref(String href) { this.href = href; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public GeographicLocation getGeographicLocation() { return geographicLocation; }
        public void setGeographicLocation(GeographicLocation geographicLocation) { this.geographicLocation = geographicLocation; }
    }

    public static class GeographicLocation {
        private String id;
        private String name;
        private Double latitude;
        private Double longitude;
        private Double altitude;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        public Double getAltitude() { return altitude; }
        public void setAltitude(Double altitude) { this.altitude = altitude; }
    }

    public static class RelatedParty {
        private String id;
        private String href;
        private String name;
        private String role;  // "owner", "operator", "maintainer"

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getHref() { return href; }
        public void setHref(String href) { this.href = href; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }

    public static class Note {
        private String id;
        private String author;
        private Instant date;
        private String text;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public Instant getDate() { return date; }
        public void setDate(Instant date) { this.date = date; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    public static class Feature {
        private String id;
        private Boolean isEnabled;
        private String name;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public Boolean getIsEnabled() { return isEnabled; }
        public void setIsEnabled(Boolean isEnabled) { this.isEnabled = isEnabled; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class AttachmentRefOrValue {
        private String id;
        private String href;
        private String attachmentType;
        private String content;
        private String description;
        private String mimeType;
        private String name;
        private String url;
        private Integer size;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getHref() { return href; }
        public void setHref(String href) { this.href = href; }
        public String getAttachmentType() { return attachmentType; }
        public void setAttachmentType(String attachmentType) { this.attachmentType = attachmentType; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public Integer getSize() { return size; }
        public void setSize(Integer size) { this.size = size; }
    }

    public static class ResourceSpecificationRef {
        private String id;
        private String href;
        private String name;
        private String version;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getHref() { return href; }
        public void setHref(String href) { this.href = href; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }
}
