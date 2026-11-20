package io.adumun.platform.spring.security.oidc.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;

class NonPersistingAuthorizedClientRepositoryTest {
    @Test
    void doesNotRetainUpstreamAuthorizedClientsAcrossCalls() {
        var repository = NonPersistingAuthorizedClientRepository.INSTANCE;
        var authorized = mock(OAuth2AuthorizedClient.class);
        var principal = mock(Authentication.class);
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);

        repository.saveAuthorizedClient(authorized, principal, request, response);
        assertThat(repository.<OAuth2AuthorizedClient>loadAuthorizedClient(
                "external", principal, request)).isNull();

        repository.removeAuthorizedClient("external", principal, request, response);
        assertThat(repository.<OAuth2AuthorizedClient>loadAuthorizedClient(
                "external", principal, request)).isNull();
    }
}
