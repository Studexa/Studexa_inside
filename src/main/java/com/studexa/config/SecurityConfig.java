package com.studexa.config;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import com.studexa.auth.oauth.OAuth2LoginSuccessHandler;

/**
 * Stateless JWT security. Auth endpoints and health/docs are public;
 * everything else needs a valid access token. Google login is only wired
 * when a client registration is configured (see GoogleOAuth2Config).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Environment env,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            AuthRateLimitFilter authRateLimitFilter,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            ObjectProvider<OAuth2LoginSuccessHandler> oauth2SuccessHandlers) throws Exception {

        boolean betaEnabled = env.getProperty("app.beta.enabled", Boolean.class, false);
        boolean googleLoginEnabled = !betaEnabled && clientRegistrations.getIfAvailable() != null;
        boolean prod = env.acceptsProfiles(Profiles.of("prod"));

        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(
                        googleLoginEnabled ? SessionCreationPolicy.IF_REQUIRED : SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    if (betaEnabled) {
                        auth.requestMatchers("/oauth2/**", "/login/oauth2/**").denyAll();
                    }
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Public: health + auth (no session needed)
                        .requestMatchers("/api/v1/health", "/api/v1/auth/**").permitAll()
                        // Admin-only API MUST come before /api/v1/** (BUG-B05)
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // All other API requires a valid JWT (secured by the OAuth2 resource server filter)
                        .requestMatchers("/api/v1/**").authenticated()
                        // Actuator health and OAuth2 login
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/oauth2/**",
                                "/login/oauth2/**")
                        .permitAll();

                    // Swagger / Actuator protection in production (BUG-L06)
                    if (prod) {
                        auth.requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/**")
                            .denyAll();
                    } else {
                        auth.requestMatchers(
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                            .permitAll();
                    }

                    // Everything else (static assets + SPA client-side routes) is public;
                    // the frontend handles its own auth redirects and the API guards its own endpoints.
                    auth.anyRequest().permitAll();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        http.headers(headers -> {
            headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                            + "frame-ancestors 'none'; base-uri 'self'"));
            headers.frameOptions(frame -> frame.deny());
            headers.contentTypeOptions(Customizer.withDefaults());
            headers.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
            if (prod) {
                headers.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000));
            }
        });

        if (googleLoginEnabled) {
            http.oauth2Login(oauth2 -> oauth2.successHandler(oauth2SuccessHandlers.getObject()));
        }

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * The limiter is part of the security chain; disable Boot's automatic
     * servlet registration so it does not run twice per request.
     */
    @Bean
    FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilterRegistration(
            AuthRateLimitFilter filter) {
        FilterRegistrationBean<AuthRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin}") String allowedOrigin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
