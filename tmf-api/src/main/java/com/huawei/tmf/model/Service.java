package com.huawei.tmf.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * TMF638 Service Inventory Management - Service entity.
 * Implements TM Forum TMF638 v4.0 specification.
 *
 * A service represents an atomic or composite service delivered to a customer.
 */
@Document(collection = "tmf_services")
public class Service {

    @Id
    private String id;

    // TMF638 standard fields
    private String href;
    private String name;
    private String description;
    private String category;

    // Service states
    private String state;  // feasibilityChecked, designed, reserved, active, inactive, terminated
    private boolean isServiceEnabled;
    private boolean hasStarted;
    private boolean isStateful;

    // Dates
    private Instant startDate;
    private Instant endDate;
    private Instant orderDate;
    private Instant startMode;

    // External references
    private String externalId;
    private String serviceType;

    // Relationships
    private ServiceSpecificationRef serviceSpecification;
    private List<ServiceCharacteristic> serviceCharacteristic;
    private List<ServiceRelationship> serviceRelationship;
    private List<SupportingService> supportingService;
    private List<SupportingResource> supportingResource;
    private List<RelatedParty> relatedParty;
    private List<Note> note;
    private List<Place> place;
    private List<Feature> feature;
    private List<RelatedServiceOrderItem> relatedServiceOrderItem;

    // Audit fields
    private Instant createdAt;
    private Instant modifiedAt;

    // Getters and Setters
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

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public boolean isServiceEnabled() { return isServiceEnabled; }
    public void setServiceEnabled(boolean serviceEnabled) { isServiceEnabled = serviceEnabled; }

    public boolean isHasStarted() { return hasStarted; }
    public void setHasStarted(boolean hasStarted) { this.hasStarted = hasStarted; }

    public boolean isStateful() { return isStateful; }
    public void setStateful(boolean stateful) { isStateful = stateful; }

    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }

    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }

    public Instant getOrderDate() { return orderDate; }
    public void setOrderDate(Instant orderDate) { this.orderDate = orderDate; }

    public Instant getStartMode() { return startMode; }
    public void setStartMode(Instant startMode) { this.startMode = startMode; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public ServiceSpecificationRef getServiceSpecification() { return serviceSpecification; }
    public void setServiceSpecification(ServiceSpecificationRef serviceSpecification) { this.serviceSpecification = serviceSpecification; }

    public List<ServiceCharacteristic> getServiceCharacteristic() { return serviceCharacteristic; }
    public void setServiceCharacteristic(List<ServiceCharacteristic> serviceCharacteristic) { this.serviceCharacteristic = serviceCharacteristic; }

    public List<ServiceRelationship> getServiceRelationship() { return serviceRelationship; }
    public void setServiceRelationship(List<ServiceRelationship> serviceRelationship) { this.serviceRelationship = serviceRelationship; }

    public List<SupportingService> getSupportingService() { return supportingService; }
    public void setSupportingService(List<SupportingService> supportingService) { this.supportingService = supportingService; }

    public List<SupportingResource> getSupportingResource() { return supportingResource; }
    public void setSupportingResource(List<SupportingResource> supportingResource) { this.supportingResource = supportingResource; }

    public List<RelatedParty> getRelatedParty() { return relatedParty; }
    public void setRelatedParty(List<RelatedParty> relatedParty) { this.relatedParty = relatedParty; }

    public List<Note> getNote() { return note; }
    public void setNote(List<Note> note) { this.note = note; }

    public List<Place> getPlace() { return place; }
    public void setPlace(List<Place> place) { this.place = place; }

    public List<Feature> getFeature() { return feature; }
    public void setFeature(List<Feature> feature) { this.feature = feature; }

    public List<RelatedServiceOrderItem> getRelatedServiceOrderItem() { return relatedServiceOrderItem; }
    public void setRelatedServiceOrderItem(List<RelatedServiceOrderItem> relatedServiceOrderItem) { this.relatedServiceOrderItem = relatedServiceOrderItem; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(Instant modifiedAt) { this.modifiedAt = modifiedAt; }

    // Nested classes for TMF638 compliance

    /**
     * Reference to the service specification.
     */
    public static class ServiceSpecificationRef {
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

    /**
     * A characteristic of the service.
     */
    public static class ServiceCharacteristic {
        private String id;
        private String name;
        private String valueType;
        private Object value;  // Can be string, number, object, etc.
        private String unitOfMeasure;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getValueType() { return valueType; }
        public void setValueType(String valueType) { this.valueType = valueType; }

        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }

        public String getUnitOfMeasure() { return unitOfMeasure; }
        public void setUnitOfMeasure(String unitOfMeasure) { this.unitOfMeasure = unitOfMeasure; }
    }

    /**
     * Relationship between services.
     */
    public static class ServiceRelationship {
        private String relationshipType;  // reliesOn, composedOf, etc.
        private ServiceRef service;

        public String getRelationshipType() { return relationshipType; }
        public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }

        public ServiceRef getService() { return service; }
        public void setService(ServiceRef service) { this.service = service; }
    }

    /**
     * Reference to a service.
     */
    public static class ServiceRef {
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

    /**
     * A supporting service (CFS -> RFS).
     */
    public static class SupportingService {
        private String id;
        private String href;
        private String name;
        private String category;
        private String state;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getHref() { return href; }
        public void setHref(String href) { this.href = href; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
    }

    /**
     * A supporting resource.
     */
    public static class SupportingResource {
        private String id;
        private String href;
        private String name;
        private String resourceType;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getHref() { return href; }
        public void setHref(String href) { this.href = href; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getResourceType() { return resourceType; }
        public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    }

    /**
     * Related party (customer, provider, etc.).
     */
    public static class RelatedParty {
        private String id;
        private String href;
        private String name;
        private String role;  // customer, provider, etc.

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getHref() { return href; }
        public void setHref(String href) { this.href = href; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }

    /**
     * A note attached to the service.
     */
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

    /**
     * Place where the service is delivered/installed.
     */
    public static class Place {
        private String id;
        private String href;
        private String name;
        private String role;  // deliveryAddress, installationAddress, etc.

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getHref() { return href; }
        public void setHref(String href) { this.href = href; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }

    /**
     * A feature of the service.
     */
    public static class Feature {
        private String id;
        private String name;
        private boolean isBundle;
        private boolean isEnabled;
        private List<ServiceCharacteristic> featureCharacteristic;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public boolean isBundle() { return isBundle; }
        public void setBundle(boolean bundle) { isBundle = bundle; }

        public boolean isEnabled() { return isEnabled; }
        public void setEnabled(boolean enabled) { isEnabled = enabled; }

        public List<ServiceCharacteristic> getFeatureCharacteristic() { return featureCharacteristic; }
        public void setFeatureCharacteristic(List<ServiceCharacteristic> featureCharacteristic) { this.featureCharacteristic = featureCharacteristic; }
    }

    /**
     * Reference to service order item.
     */
    public static class RelatedServiceOrderItem {
        private String orderItemId;
        private String serviceOrderHref;
        private String serviceOrderId;
        private String role;
        private String itemAction;

        public String getOrderItemId() { return orderItemId; }
        public void setOrderItemId(String orderItemId) { this.orderItemId = orderItemId; }

        public String getServiceOrderHref() { return serviceOrderHref; }
        public void setServiceOrderHref(String serviceOrderHref) { this.serviceOrderHref = serviceOrderHref; }

        public String getServiceOrderId() { return serviceOrderId; }
        public void setServiceOrderId(String serviceOrderId) { this.serviceOrderId = serviceOrderId; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getItemAction() { return itemAction; }
        public void setItemAction(String itemAction) { this.itemAction = itemAction; }
    }
}
