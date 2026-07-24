package io.github.erselseyit.basestation.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;

/**
 * Configuration for fine-grained permission-based access control.
 *
 * <p>This configuration registers the custom {@link ResourcePermissionEvaluator}
 * with Spring Security's method security, enabling {@code hasPermission()} checks
 * in {@code @PreAuthorize} annotations.
 *
 * <h2>Usage in Services:</h2>
 * <p>Import this configuration in your service's security config:
 * <pre>
 * &#64;Configuration
 * &#64;EnableMethodSecurity
 * &#64;Import(PermissionConfig.class)
 * public class ServiceSecurityConfig { }
 * </pre>
 *
 * <p>Or use component scanning if common module is on classpath.
 */
@Configuration
public class PermissionConfig {

    /**
     * Custom permission evaluator for resource-level access control.
     */
    @Bean
    public PermissionEvaluator permissionEvaluator() {
        return new ResourcePermissionEvaluator();
    }

    /**
     * Method security expression handler with custom permission evaluator.
     */
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            PermissionEvaluator permissionEvaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }
}
