package com.example.courses.shared.security;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless bearer-token API. The filter chain only separates public from authenticated endpoints; role
 * and ownership rules live next to each endpoint in {@code @PreAuthorize}.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(RateLimitProperties.class)
public class SecurityConfig {

    /**
     * Actuator runs on its own port, reachable only inside the deployment network, so Prometheus and the
     * orchestrator's probes can read it without credentials. Matching both port and path means that even
     * if both ports were configured the same, this chain could never open the API itself.
     */
    @Bean
    @Order(1)
    SecurityFilterChain managementSecurity(HttpSecurity http, ManagementPort managementPort) throws Exception {
        return http
                .securityMatcher((HttpServletRequest request) -> managementPort.matches(request.getLocalPort())
                        && request.getRequestURI().startsWith("/actuator"))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurity(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
                                    ProblemDetailsSecurityHandler problemHandler, RateLimitProperties rateLimits,
                                    RedisBuckets rateLimitBuckets, MeterRegistry meterRegistry) throws Exception {
        if (rateLimits.enabled()) {
            // After bearer-token authentication, so per-user limits know who the caller is.
            http.addFilterAfter(new RateLimitFilter(rateLimits, rateLimitBuckets, problemHandler, meterRegistry),
                    BearerTokenAuthenticationFilter.class);
        }
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/token", "/api/auth/register").permitAll()
                        // Certificate verification: whoever is shown a certificate checks its code, without an account.
                        .requestMatchers(HttpMethod.GET, "/api/certificates/*").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .build();
    }

    /** BCrypt through the delegating encoder, so stored hashes carry their algorithm and can be upgraded. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
