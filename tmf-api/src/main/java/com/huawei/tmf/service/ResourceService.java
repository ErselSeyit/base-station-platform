package com.huawei.tmf.service;

import static com.huawei.tmf.constants.TMFConstants.STATE_DISABLED;

import com.huawei.tmf.model.Resource;
import com.huawei.tmf.repository.ResourceRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for TMF639 Resource Inventory Management.
 */
@Service
public class ResourceService {

    private static final Logger log = LoggerFactory.getLogger(ResourceService.class);

    private final ResourceRepository resourceRepository;
    private final MongoTemplate mongoTemplate;

    // In-memory listener registry (in production, use Redis or database)
    private final Map<String, Map<String, Object>> listeners = new ConcurrentHashMap<>();

    public ResourceService(ResourceRepository resourceRepository, MongoTemplate mongoTemplate) {
        this.resourceRepository = resourceRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Find resources with optional filters.
     */
    public Page<Resource> findResources(String category, String resourceType,
                                         String operationalState, String administrativeState,
                                         String name, String externalId,
                                         Pageable pageable) {
        Query query = new Query();

        if (category != null && !category.isEmpty()) {
            query.addCriteria(Criteria.where("category").is(category));
        }
        if (resourceType != null && !resourceType.isEmpty()) {
            query.addCriteria(Criteria.where("resourceSpecification.@type").is(resourceType));
        }
        if (operationalState != null && !operationalState.isEmpty()) {
            query.addCriteria(Criteria.where("operationalState").is(operationalState));
        }
        if (administrativeState != null && !administrativeState.isEmpty()) {
            query.addCriteria(Criteria.where("administrativeState").is(administrativeState));
        }
        if (name != null && !name.isEmpty()) {
            query.addCriteria(Criteria.where("name").regex(name, "i"));
        }
        if (externalId != null && !externalId.isEmpty()) {
            query.addCriteria(Criteria.where("externalId").is(externalId));
        }

        long total = mongoTemplate.count(query, Resource.class);
        query.with(pageable);
        List<Resource> resources = mongoTemplate.find(query, Resource.class);

        return new PageImpl<>(resources, pageable, total);
    }

    /**
     * Find resource by ID.
     */
    public Optional<Resource> findById(String id) {
        return resourceRepository.findById(id);
    }

    /**
     * Find resource by external ID.
     */
    public Optional<Resource> findByExternalId(String externalId) {
        return resourceRepository.findByExternalId(externalId);
    }

    /**
     * Create a new resource.
     */
    public Resource create(Resource resource) {
        if (resource.getId() == null) {
            resource.setId(UUID.randomUUID().toString());
        }

        Instant now = Instant.now();
        resource.setCreatedAt(now);
        resource.setModifiedAt(now);

        if (resource.getLifecycleState() == null) {
            resource.setLifecycleState("planned");
        }
        if (resource.getOperationalState() == null) {
            resource.setOperationalState(STATE_DISABLED);
        }
        if (resource.getAdministrativeState() == null) {
            resource.setAdministrativeState("locked");
        }

        Resource saved = resourceRepository.save(resource);
        notifyListeners("ResourceCreateEvent", saved);
        return saved;
    }

    /**
     * Update a resource (full replacement).
     */
    public Resource update(Resource resource) {
        resource.setModifiedAt(Instant.now());
        Resource saved = resourceRepository.save(resource);
        notifyListeners("ResourceAttributeValueChangeEvent", saved);
        return saved;
    }

    /**
     * Partial update of a resource.
     */
    public Optional<Resource> patch(String id, Map<String, Object> updates) {
        Optional<Resource> existing = resourceRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Resource resource = existing.get();
        applyPatch(resource, updates);
        resource.setModifiedAt(Instant.now());

        Resource saved = resourceRepository.save(resource);
        notifyListeners("ResourceAttributeValueChangeEvent", saved);
        return Optional.of(saved);
    }

    /**
     * Delete a resource.
     */
    public boolean delete(String id) {
        Optional<Resource> existing = resourceRepository.findById(id);
        if (existing.isEmpty()) {
            return false;
        }

        resourceRepository.deleteById(id);
        notifyListeners("ResourceDeleteEvent", existing.get());
        return true;
    }

    /**
     * Find child resources.
     */
    public List<Resource> findChildren(String parentId) {
        return resourceRepository.findChildrenByParentId(parentId);
    }

    /**
     * Get resource statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalResources", resourceRepository.count());

        // Count by category
        Map<String, Long> byCategory = new HashMap<>();
        byCategory.put("baseStation", resourceRepository.countByCategory("baseStation"));
        byCategory.put("antenna", resourceRepository.countByCategory("antenna"));
        byCategory.put("radio", resourceRepository.countByCategory("radio"));
        byCategory.put("power", resourceRepository.countByCategory("power"));
        byCategory.put("transport", resourceRepository.countByCategory("transport"));
        stats.put("byCategory", byCategory);

        // Count by operational state
        Map<String, Long> byState = new HashMap<>();
        byState.put("enabled", resourceRepository.countByOperationalState("enabled"));
        byState.put(STATE_DISABLED, resourceRepository.countByOperationalState(STATE_DISABLED));
        stats.put("byOperationalState", byState);

        return stats;
    }

    /**
     * Register a listener for resource events.
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
     * Apply partial updates to a resource.
     */
    private void applyPatch(Resource resource, Map<String, Object> updates) {
        updates.forEach((key, value) -> {
            switch (key) {
                case "name" -> resource.setName((String) value);
                case "description" -> resource.setDescription((String) value);
                case "category" -> resource.setCategory((String) value);
                case "lifecycleState" -> resource.setLifecycleState((String) value);
                case "operationalState" -> resource.setOperationalState((String) value);
                case "administrativeState" -> resource.setAdministrativeState((String) value);
                case "usageState" -> resource.setUsageState((String) value);
                case "startOperatingDate" -> resource.setStartOperatingDate(Instant.parse((String) value));
                case "endOperatingDate" -> resource.setEndOperatingDate(Instant.parse((String) value));
                default -> {
                    // Handle nested objects if needed
                }
            }
        });
    }

    /**
     * Notify registered listeners of resource events.
     * In production, this would use a message queue or webhook calls.
     */
    private void notifyListeners(String eventType, Resource resource) {
        // Log the event (in production, send to webhook endpoints)
        listeners.values().forEach(listener -> {
            String callback = (String) listener.get("callback");
            if (callback != null) {
                // Callback notification would be sent via HTTP POST
                log.debug("[TMF639] Event {} for resource {} -> {}", eventType, resource.getId(), callback);
            }
        });
    }
}
