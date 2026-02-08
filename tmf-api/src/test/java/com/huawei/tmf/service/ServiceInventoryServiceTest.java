package com.huawei.tmf.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.huawei.tmf.model.Service;
import com.huawei.tmf.repository.ServiceRepository;

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
 * Unit tests for ServiceInventoryService.
 */
@ExtendWith(MockitoExtension.class)
class ServiceInventoryServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private ServiceInventoryService serviceInventoryService;

    private Service testService;

    @BeforeEach
    void setUp() {
        testService = createTestService("service-1");
    }

    private Service createTestService(String id) {
        Service service = new Service();
        service.setId(id);
        service.setName("Test Service");
        service.setCategory("CFS");
        service.setState("designed");
        service.setServiceEnabled(false);
        service.setHasStarted(false);
        return service;
    }

    // ========== findById Tests ==========

    @Test
    void findById_WhenExists_ReturnsService() {
        when(serviceRepository.findById("service-1")).thenReturn(Optional.of(testService));

        Optional<Service> result = serviceInventoryService.findById("service-1");

        assertTrue(result.isPresent());
        assertEquals("service-1", result.get().getId());
    }

    @Test
    void findById_WhenNotExists_ReturnsEmpty() {
        when(serviceRepository.findById("non-existent")).thenReturn(Optional.empty());

        Optional<Service> result = serviceInventoryService.findById("non-existent");

        assertFalse(result.isPresent());
    }

    // ========== create Tests ==========

    @Test
    void create_SetsDefaultValues() {
        Service newService = new Service();
        newService.setName("New Service");

        when(serviceRepository.save(any(Service.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Service result = serviceInventoryService.create(newService);

        assertNotNull(result.getId());
        assertNotNull(result.getCreatedAt());
        assertEquals("designed", result.getState());
    }

    // ========== activate Tests ==========

    @Test
    void activate_WhenExists_ActivatesService() {
        when(serviceRepository.findById("service-1")).thenReturn(Optional.of(testService));
        when(serviceRepository.save(any(Service.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Service> result = serviceInventoryService.activate("service-1");

        assertTrue(result.isPresent());
        assertEquals("active", result.get().getState());
        assertTrue(result.get().isServiceEnabled());
        assertTrue(result.get().isHasStarted());
    }

    @Test
    void activate_WhenNotExists_ReturnsEmpty() {
        when(serviceRepository.findById("non-existent")).thenReturn(Optional.empty());

        Optional<Service> result = serviceInventoryService.activate("non-existent");

        assertFalse(result.isPresent());
    }

    // ========== deactivate Tests ==========

    @Test
    void deactivate_WhenExists_DeactivatesService() {
        testService.setState("active");
        testService.setServiceEnabled(true);
        when(serviceRepository.findById("service-1")).thenReturn(Optional.of(testService));
        when(serviceRepository.save(any(Service.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Service> result = serviceInventoryService.deactivate("service-1");

        assertTrue(result.isPresent());
        assertEquals("inactive", result.get().getState());
        assertFalse(result.get().isServiceEnabled());
    }

    // ========== terminate Tests ==========

    @Test
    void terminate_WhenExists_TerminatesService() {
        when(serviceRepository.findById("service-1")).thenReturn(Optional.of(testService));
        when(serviceRepository.save(any(Service.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<Service> result = serviceInventoryService.terminate("service-1");

        assertTrue(result.isPresent());
        assertEquals("terminated", result.get().getState());
        assertFalse(result.get().isServiceEnabled());
        assertNotNull(result.get().getEndDate());
    }

    // ========== delete Tests ==========

    @Test
    void delete_WhenExists_ReturnsTrue() {
        when(serviceRepository.findById("service-1")).thenReturn(Optional.of(testService));
        doNothing().when(serviceRepository).deleteById("service-1");

        boolean result = serviceInventoryService.delete("service-1");

        assertTrue(result);
        verify(serviceRepository).deleteById("service-1");
    }

    @Test
    void delete_WhenNotExists_ReturnsFalse() {
        when(serviceRepository.findById("non-existent")).thenReturn(Optional.empty());

        boolean result = serviceInventoryService.delete("non-existent");

        assertFalse(result);
        verify(serviceRepository, never()).deleteById(any());
    }

    // ========== findByExternalId Tests ==========

    @Test
    void findByExternalId_WhenExists_ReturnsService() {
        testService.setExternalId("ext-123");
        when(serviceRepository.findByExternalId("ext-123")).thenReturn(Optional.of(testService));

        Optional<Service> result = serviceInventoryService.findByExternalId("ext-123");

        assertTrue(result.isPresent());
        assertEquals("ext-123", result.get().getExternalId());
    }

    // ========== findByResourceId Tests ==========

    @Test
    void findByResourceId_ReturnsServices() {
        when(serviceRepository.findBysSupportingResourceId("resource-1")).thenReturn(List.of(testService));

        List<Service> result = serviceInventoryService.findByResourceId("resource-1");

        assertEquals(1, result.size());
    }
}
