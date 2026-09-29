package com.example.courses.shared.mcp;

import com.example.courses.idempotency.application.IdempotencyKeyReusedException;
import com.example.courses.shared.application.InvalidCursorException;
import com.example.courses.shared.application.ResourceNotFoundException;
import com.example.courses.shared.domain.BusinessRuleViolationException;
import com.example.courses.shared.domain.ConflictException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.aopalliance.intercept.MethodInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.util.stream.Collectors;

/**
 * The MCP counterpart of the REST API's GlobalExceptionHandler: whatever a tool throws, the AI client only ever
 * sees a message written by this application. Messages of the application's own exceptions pass through, and
 * anything unexpected (a SQL error, a bug) becomes a generic message and is logged server-side.
 * <p>
 * Applied as the outermost interceptor of every {@code @McpTool} method, so it also covers what the authorization
 * and validation interceptors throw before the tool body runs.
 */
@Configuration(proxyBeanMethods = false)
public class McpToolErrors {

    private static final Logger log = LoggerFactory.getLogger(McpToolErrors.class);

    /**
     * What the client sees. Spring AI 2.0 renders a failed call as the exception's message followed by its root
     * cause's message; the empty cause keeps that from repeating the message.
     */
    static final class McpToolException extends RuntimeException {

        McpToolException(String message) {
            super(message, new RuntimeException("", null, false, false) {
            });
        }
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor mcpToolErrorAdvisor() {
        MethodInterceptor translateErrors = invocation -> {
            try {
                return invocation.proceed();
            } catch (McpToolException e) {
                throw e;
            } catch (Exception e) {
                throw new McpToolException(clientMessage(e));
            }
        };
        var advisor = new DefaultPointcutAdvisor(AnnotationMatchingPointcut.forMethodAnnotation(McpTool.class),
                translateErrors);
        advisor.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return advisor;
    }

    static String clientMessage(Exception e) {
        return switch (e) {
            case ResourceNotFoundException notFound -> notFound.getMessage();
            case ConflictException conflict -> conflict.getMessage();
            case BusinessRuleViolationException rule -> rule.getMessage();
            case IdempotencyKeyReusedException reused -> reused.getMessage();
            case InvalidCursorException cursor -> cursor.getMessage();
            case AccessDeniedException denied -> "You are not allowed to perform this operation";
            case ConstraintViolationException invalid -> "Invalid arguments: " + invalid.getConstraintViolations()
                    .stream().map(McpToolErrors::describe).sorted().collect(Collectors.joining("; "));
            case OptimisticLockingFailureException stale ->
                    "The resource was modified by another request; reload it and retry";
            case DataIntegrityViolationException integrity -> {
                log.warn("Data integrity violation in an MCP tool", integrity);
                yield "The request conflicts with existing data (duplicate value or resource still in use)";
            }
            default -> {
                log.error("Unhandled error in an MCP tool", e);
                yield "An unexpected error occurred";
            }
        };
    }

    /** "name must not be blank" rather than "createCategory.name must not be blank". */
    private static String describe(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        return path.substring(path.lastIndexOf('.') + 1) + " " + violation.getMessage();
    }
}
