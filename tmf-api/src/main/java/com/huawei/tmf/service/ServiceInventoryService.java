package com.huawei.tmf.service;

import static com.huawei.tmf.constants.TMFConstants.*;

import com.huawei.tmf.model.Service;
import com.huawei.tmf.repository.ServiceRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for TMF638 Service Inventory Management.
 */
@org.springframework.stereotype.Service
@SuppressWarnings("null") // Spring Data MongoDB operations guarantee non-null returns
public class ServiceInventoryService {

    private static final Logger log = LoggerFactory.getLogger(ServiceInventoryService.class);

    private final ServiceRepository serviceRepository;
    private final MongoTemplate mongoTemplate;

    // In-memory listener registry
    private final Map<String, Map<String, Object>> listeners = new ConcurrentHashMap<>();

    public ServiceInventoryService(ServiceRepository serviceRepository, MongoTemplate mongoTemplate) {
        this.serviceRepository = serviceRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Find services with optional filters.
     */
    public Page<Service> findServices(String state, String category, String serviceType,
                                        String name, String externalId, String relatedPartyId,
                                        Pageable pageable) {
        Query query = new Query();

        if (state != null && !state.isEmpty()) {
            query.addCriteria(Criteria.where(FIELD_STATE).is(state));
        }
        if (category != null && !category.isEmpty()) {
            query.addCriteria(Criteria.where("category").is(category));
        }
        if (serviceType != null && !serviceType.isEmpty()) {
            query.addCriteria(Criteria.where("serviceType").is(serviceType));
        }
        if (name != null && !name.isEmpty()) {
            query.addCriteria(Criteria.where("name").regex(name, "i"));
        }
        if (externalId != null && !externalId.isEmpty()) {
            query.addCriteria(Criteria.where("externalId").is(externalId));
        }
        if (relatedPartyId != null && !relatedPartyId.isEmpty()) {
            query.addCriteria(Criteria.where("relatedParty.id").is(relatedPartyId));
        }

        long total = mongoTemplate.count(query, Service.class);
        query.with(pageable);
        List<Service> services = mongoTemplate.find(query, Service.class);

        return new PageImpl<>(services, pageable, total);
    }

    /**
     * Find service by ID.
     */
    public Optional<Service> findById(String id) {
        return serviceRepository.findById(id);
    }

    /**
     * Find service by external ID.
     */
    public Optional<Service> findByExternalId(String externalId) {
        return serviceRepository.findByExternalId(externalId);
    }

    /**
     * Find services by supporting resource ID.
     */
    public List<Service> findByResourceId(String resourceId) {
        return serviceRepository.findBysSupportingResourceId(resourceId);
    }

    /**
     * Create a new service.
     */
    public Service create(Service service) {
        if (service.getId() == null) {
            service.setId(UUID.randomUUID().toString());
        }

        Instant now = Instant.now();
        service.setCreatedAt(now);
        service.setModifiedAt(now);

        if (service.getState() == null) {
            service.setState(STATE_DESIGNED);
        }

        Service saved = serviceRepository.save(service);
        notifyListeners("ServiceCreateEvent", saved);
        return saved;
    }

    /**
     * Update a service (full replacement).
     */
    public Service update(Service service) {
        service.setModifiedAt(Instant.now());
        Service saved = serviceRepository.save(service);
        notifyListeners("ServiceAttributeValueChangeEvent", saved);
        return saved;
    }

    /**
     * Partial update of a service.
     */
    public Optional<Service> patch(String id, Map<String, Object> updates) {
        Optional<Service> existing = serviceRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Service service = existing.get();
        String oldState = service.getState();
        applyPatch(service, updates);
        service.setModifiedAt(Instant.now());

        Service saved = serviceRepository.save(service);

        // Check if state changed
        if (updates.containsKey(FIELD_STATE) && !oldState.equals(service.getState())) {
            notifyListeners(EVENT_SERVICE_STATE_CHANGE, saved);
        } else {
            notifyListeners("ServiceAttributeValueChangeEvent", saved);
        }

        return Optional.of(saved);
    }

    /**
     * Delete a service.
     */
    public boolean delete(String id) {
        Optional<Service> existing = serviceRepository.findById(id);
        if (existing.isEmpty()) {
            return false;
        }

        serviceRepository.deleteById(id);
        notifyListeners("ServiceDeleteEvent", existing.get());
        return true;
    }

    /**
     * Activate a service.
     */
    public Optional<Service> activate(String id) {
        Optional<Service> existing = serviceRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Service service = existing.get();
        service.setState(STATE_ACTIVE);
        service.setServiceEnabled(true);
        service.setHasStarted(true);
        service.setStartDate(Instant.now());
        service.setModifiedAt(Instant.now());

        Service saved = serviceRepository.save(service);
        notifyListeners(EVENT_SERVICE_STATE_CHANGE, saved);
        return Optional.of(saved);
    }

    /**
     * Deactivate a service.
     */
    public Optional<Service> deactivate(String id) {
        Optional<Service> existing = serviceRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Service service = existing.get();
        service.setState(STATE_INACTIVE);
        service.setServiceEnabled(false);
        service.setModifiedAt(Instant.now());

        Service saved = serviceRepository.save(service);
        notifyListeners(EVENT_SERVICE_STATE_CHANGE, saved);
        return Optional.of(saved);
    }

    /**
     * Terminate a service.
     */
    public Optional<Service> terminate(String id) {
        Optional<Service> existing = serviceRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Service service = existing.get();
        service.setState(STATE_TERMINATED);
        service.setServiceEnabled(false);
        service.setEndDate(Instant.now());
        service.setModifiedAt(Instant.now());

        Service saved = serviceRepository.save(service);
        notifyListeners(EVENT_SERVICE_STATE_CHANGE, saved);
        return Optional.of(saved);
    }

    /**
     * Get service statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalServices", serviceRepository.count());

        // Count by state
        Map<String, Long> byState = new HashMap<>();
        byState.put(STATE_DESIGNED, serviceRepository.countByState(STATE_DESIGNED));
        byState.put("reserved", serviceRepository.countByState("reserved"));
        byState.put(STATE_ACTIVE, serviceRepository.countByState(STATE_ACTIVE));
        byState.put(STATE_INACTIVE, serviceRepository.countByState(STATE_INACTIVE));
        byState.put(STATE_TERMINATED, serviceRepository.countByState(STATE_TERMINATED));
        stats.put("byState", byState);

        // Count by category
        Map<String, Long> byCategory = new HashMap<>();
        byCategory.put("CFS", serviceRepository.countByCategory("CFS"));
        byCategory.put("RFS", serviceRepository.countByCategory("RFS"));
        stats.put("byCategory", byCategory);

        return stats;
    }

    /**
     * Register a listener for service events.
     */
    public Map<String, Object> registerListener(Map<String, Object> subscription) {
        String id = UUID.randomUUID().toString();
        subscription.put("id", id);
        subscription.put("createdAt", Instant.now().toString());
        listeners.put(id, subscription);
        return subscription;
    }

    /**
     * Unregister a listener.
     */
    public void unregisterListener(String id) {
        listeners.remove(id);
    }

    /**
     * Apply partial updates to a service.
     */
    private void applyPatch(Service service, Map<String, Object> updates) {
        updates.forEach((key, value) -> {
            switch (key) {
                case "name" -> service.setName((String) value);
                case "description" -> service.setDescription((String) value);
                case "category" -> service.setCategory((String) value);
                case FIELD_STATE -> service.setState((String) value);
                case "isServiceEnabled" -> service.setServiceEnabled((Boolean) value);
                case "startDate" -> service.setStartDate(Instant.parse((String) value));
                case "endDate" -> service.setEndDate(Instant.parse((String) value));
                default -> {
                    // Handle nested objects if needed
                }
            }
        });
    }

    /**
     * Notify registered listeners of service events.
     */
    private void notifyListeners(String eventType, Service service) {
        listeners.values().forEach(listener -> {
            String callback = (String) listener.get("callback");
            if (callback != null) {
                // Callback notification would be sent via HTTP POST
                log.debug("[TMF638] Event {} for service {} -> {}", eventType, service.getId(), callback);
            }
        });
    }
}
