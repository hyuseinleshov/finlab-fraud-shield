package com.finlab.accounts.config;

import com.finlab.accounts.filter.ApiKeyAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 6 configuration for API key authentication.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    public SecurityConfiguration(ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // Disable CSRF for stateless API (service-to-service communication)
                .csrf(csrf -> csrf.disable())

                // Stateless session management (no session cookies)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Allow health check endpoint without authentication
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated())

                // Add API key filter before standard authentication
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }
}
