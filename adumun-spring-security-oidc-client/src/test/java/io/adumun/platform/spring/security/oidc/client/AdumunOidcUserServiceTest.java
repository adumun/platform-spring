package io.adumun.platform.spring.security.oidc.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class AdumunOidcUserServiceTest {
    @Test
    void consumerPolicyRunsBeforeAUserIsReturned() {
        var principal = mock(OidcUser.class);
        var accepted = new AtomicBoolean();
        var service = new AdumunOidcUserService(
                request -> principal, (request, user) -> {
                    assertThat(user).isSameAs(principal);
                    accepted.set(true);
                });

        assertThat(service.loadUser(null)).isSameAs(principal);
        assertThat(accepted).isTrue();
    }

    @Test
    void upstreamPrincipalFailureIsAlsoSanitized() {
        var service = new AdumunOidcUserService(
                request -> { throw new IllegalStateException("sensitive-provider-detail"); },
                (request, user) -> {});
        assertThatThrownBy(() -> service.loadUser(null))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception -> {
                    assertThat(exception.getError().getErrorCode()).isEqualTo("identity_rejected");
                    assertThat(exception.getMessage()).doesNotContain("sensitive-provider-detail");
                });
    }

    @Test
    void consumerPolicyRejectionFailsClosedWithoutLeakingDetails() {
        var service = new AdumunOidcUserService(
                request -> mock(OidcUser.class),
                (request, user) -> { throw new IllegalStateException("internal-secret-detail"); });

        assertThatThrownBy(() -> service.loadUser(null))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception -> {
                    assertThat(exception.getError().getErrorCode()).isEqualTo("identity_rejected");
                    assertThat(exception.getMessage()).doesNotContain("internal-secret-detail");
                });
    }
}
