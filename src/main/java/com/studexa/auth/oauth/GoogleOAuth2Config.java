package com.studexa.auth.oauth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.util.StringUtils;

/**
 * Provides a Google client registration only when GOOGLE_LOGIN_ENABLED=true
 * and both credentials are present. SecurityConfig wires oauth2Login only
 * when this repository bean exists, so the app starts without Google setup.
 */
@Configuration
@EnableConfigurationProperties(GoogleOAuthProperties.class)
@ConditionalOnProperty(prefix = "app.oauth.google", name = "enabled", havingValue = "true")
public class GoogleOAuth2Config {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(GoogleOAuthProperties properties) {
        if (!StringUtils.hasText(properties.clientId())
                || !StringUtils.hasText(properties.clientSecret())) {
            throw new IllegalStateException(
                    "GOOGLE_LOGIN_ENABLED is true but GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET are not set.");
        }

        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId(properties.clientId())
                .clientSecret(properties.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(OidcScopes.OPENID, OidcScopes.EMAIL, OidcScopes.PROFILE)
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName(IdTokenClaimNames.SUB)
                .issuerUri("https://accounts.google.com")
                .clientName("Google")
                .build();

        return new InMemoryClientRegistrationRepository(registration);
    }
}
