package com.huawei.tmf.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.huawei.tmf.model.Resource;
import com.huawei.tmf.repository.ResourceRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.*;

/**
 * Unit tests for ResourceService.
 */
@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private ResourceService resourceService;

    private Resource testResource;

    @BeforeEach
    void setUp() {
        testResource = createTestResource("resource-1");
    }

    private Resource createTestResource(String id) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setName("Test Resource");
        resource.setCategory("Equipment");
        resource.setResourceType("PhysicalResource");
        resource.setOperationalState("enabled");
        resource.setAdministrativeState("unlocked");
        return resource;
    }

    // ========== findById Tests ==========

    @Test
    void findById_WhenExists_ReturnsResource() {
        when(resourceRepository.findById("resource-1")).thenReturn(Optional.of(testResource));

        Optional<Resource> result = resourceService.findById("resource-1");

        assertTrue(result.isPresent());
        assertEquals("resource-1", result.get().getId());
    }

    @Test
    void findById_WhenNotExists_ReturnsEmpty() {
        when(resourceRepository.findById("non-existent")).thenReturn(Optional.empty());

        Optional<Resource> result = resourceService.findById("non-existent");

        assertFalse(result.isPresent());
    }

    // ========== create Tests ==========

    @Test
    void create_SetsDefaultValues() {
        Resource newResource = new Resource();
        newResource.setName("New Resource");

        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource result = resourceService.create(newResource);

        assertNotNull(result.getId());
        assertNotNull(result.getCreatedAt());
    }

    @Test
    void create_PreservesExistingId() {
        Resource resourceWithId = createTestResource("custom-id");

        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Resource result = resourceService.create(resourceWithId);

        assertEquals("custom-id", result.getId());
    }

    // ========== update Tests ==========

    @Test
    void update_SetsModifiedAtAndSaves() {
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        testResource.setName("Updated Name");
        Resource result = resourceService.update(testResource);

        assertEquals("Updated Name", result.getName());
        assertNotNull(result.getModifiedAt());
        verify(resourceRepository).save(testResource);
    }

    // ========== delete Tests ==========

    @Test
    void delete_WhenExists_ReturnsTrue() {
        when(resourceRepository.findById("resource-1")).thenReturn(Optional.of(testResource));
        doNothing().when(resourceRepository).deleteById("resource-1");

        boolean result = resourceService.delete("resource-1");

        assertTrue(result);
        verify(resourceRepository).deleteById("resource-1");
    }

    @Test
    void delete_WhenNotExists_ReturnsFalse() {
        when(resourceRepository.findById("non-existent")).thenReturn(Optional.empty());

        boolean result = resourceService.delete("non-existent");

        assertFalse(result);
        verify(resourceRepository, never()).deleteById(any());
    }

    // ========== findByExternalId Tests ==========

    @Test
    void findByExternalId_WhenExists_ReturnsResource() {
        testResource.setExternalId("ext-123");
        when(resourceRepository.findByExternalId("ext-123")).thenReturn(Optional.of(testResource));

        Optional<Resource> result = resourceService.findByExternalId("ext-123");

        assertTrue(result.isPresent());
        assertEquals("ext-123", result.get().getExternalId());
    }

    // ========== findChildren Tests ==========

    @Test
    void findChildren_ReturnsChildResources() {
        Resource childResource = createTestResource("child-1");
        when(resourceRepository.findChildrenByParentId("resource-1")).thenReturn(List.of(childResource));

        List<Resource> result = resourceService.findChildren("resource-1");

        assertEquals(1, result.size());
        assertEquals("child-1", result.get(0).getId());
    }

    // ========== patch Tests ==========

    @Test
    void patch_WhenExists_AppliesUpdates() {
        when(resourceRepository.findById("resource-1")).thenReturn(Optional.of(testResource));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> updates = Map.of("name", "Patched Name", "operationalState", "disabled");
        Optional<Resource> result = resourceService.patch("resource-1", updates);

        assertTrue(result.isPresent());
        assertEquals("Patched Name", result.get().getName());
        assertEquals("disabled", result.get().getOperationalState());
    }

    @Test
    void patch_WhenNotExists_ReturnsEmpty() {
        when(resourceRepository.findById("non-existent")).thenReturn(Optional.empty());

        Optional<Resource> result = resourceService.patch("non-existent", Map.of("name", "New Name"));

        assertFalse(result.isPresent());
    }
}
