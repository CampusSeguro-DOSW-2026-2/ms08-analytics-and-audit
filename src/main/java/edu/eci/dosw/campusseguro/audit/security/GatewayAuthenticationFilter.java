package edu.eci.dosw.campusseguro.audit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request with the headers the API Gateway injects. MS08 never sees the JWT
 * and does not check the service key (the Gateway does).
 *
 * <ul>
 *   <li>{@code X-Service-Name} = ms01..ms08: request from another microservice. It gets the
 *       {@link Authorities#SERVICE} authority.</li>
 *   <li>{@code X-User-Id} (UUID) + {@code X-User-Permission}: request from a user. Each
 *       permission code becomes an authority.</li>
 *   <li>No identity headers: the request continues anonymous (the security rules answer 401).</li>
 *   <li>Headers that are present but invalid: 401.</li>
 * </ul>
 */
@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVICE_NAME = Pattern.compile("^ms0[1-8]$", Pattern.CASE_INSENSITIVE);

    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public GatewayAuthenticationFilter(RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String service = request.getHeader(IdentityHeaders.SERVICE_NAME);
        String userId = request.getHeader(IdentityHeaders.USER_ID);
        String permissions = request.getHeader(IdentityHeaders.USER_PERMISSION);

        if (!isBlank(service)) {
            if (!SERVICE_NAME.matcher(service.strip()).matches()) {
                reject(request, response);
                return;
            }
            authenticate(request, service.strip().toLowerCase(), List.of(new SimpleGrantedAuthority(Authorities.SERVICE)));
        } else if (!isBlank(userId) || !isBlank(permissions)) {
            List<GrantedAuthority> authorities = permissionsOf(permissions);
            if (userId == null || !UUID.matcher(userId.strip()).matches() || authorities.isEmpty()) {
                reject(request, response);
                return;
            }
            authenticate(request, userId.strip(), authorities);
        }
        filterChain.doFilter(request, response);
    }

    private List<GrantedAuthority> permissionsOf(String header) {
        if (isBlank(header)) {
            return List.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .distinct()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }

    private void authenticate(HttpServletRequest request, String principal, List<? extends GrantedAuthority> authorities) {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(request, response, null);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
