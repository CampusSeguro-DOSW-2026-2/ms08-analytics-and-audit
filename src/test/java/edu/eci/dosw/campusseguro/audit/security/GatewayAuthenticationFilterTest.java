package edu.eci.dosw.campusseguro.audit.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Checks how the identity headers of the Gateway become an authentication. */
class GatewayAuthenticationFilterTest {

    private static final String USER = "3f2a9c1e-7b4d-4e8a-9a55-0c1d2e3f4a5b";

    private final RestAuthenticationEntryPoint entryPoint = mock(RestAuthenticationEntryPoint.class);
    private final GatewayAuthenticationFilter filter = new GatewayAuthenticationFilter(entryPoint);
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    private Authentication run(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private Set<String> authorities(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    @Test
    void userPermissionsBecomeAuthorities() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_ID, USER);
        request.addHeader(IdentityHeaders.USER_ROLE, "ORGANIZADOR");
        request.addHeader(IdentityHeaders.USER_PERMISSION,
                " ms08:audit_drill:read , ms08:indicators_drill:read,ms08:audit_drill:read");

        Authentication authentication = run(request);

        assertEquals(USER, authentication.getName());
        assertEquals(Set.of("ms08:audit_drill:read", "ms08:indicators_drill:read"), authorities(authentication));
        verify(chain).doFilter(any(), any());
    }

    @Test
    void aKnownServiceGetsTheServiceAuthority() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.SERVICE_NAME, "MS06");

        Authentication authentication = run(request);

        assertEquals("ms06", authentication.getName());
        assertEquals(Set.of(Authorities.SERVICE), authorities(authentication));
        verify(chain).doFilter(any(), any());
    }

    @Test
    void anUnknownServiceNameIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.SERVICE_NAME, "ms99");

        assertNull(run(request));
        verify(entryPoint).commence(any(), any(), isNull());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void aRequestWithoutIdentityContinuesAnonymous() throws Exception {
        assertNull(run(new MockHttpServletRequest()));
        verify(chain).doFilter(any(), any());
    }

    @Test
    void aUserIdThatIsNotAUuidIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_ID, "not-a-uuid");
        request.addHeader(IdentityHeaders.USER_PERMISSION, "ms08:audit_drill:read");

        assertNull(run(request));
        verify(entryPoint).commence(any(), any(), isNull());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void aUserWithoutPermissionsIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_ID, USER);

        assertNull(run(request));
        verify(entryPoint).commence(any(), any(), isNull());
    }

    @Test
    void permissionsWithoutUserIdAreRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_PERMISSION, "ms08:audit_drill:read");

        assertNull(run(request));
        verify(entryPoint).commence(any(), any(), isNull());
    }

    @Test
    void aBlankPermissionListIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_ID, USER);
        request.addHeader(IdentityHeaders.USER_PERMISSION, " , ");

        assertNull(run(request));
        verify(entryPoint).commence(any(), any(), isNull());
    }
}
