package edu.eci.dosw.campusseguro.audit.config;

import edu.eci.dosw.campusseguro.audit.security.Authorities;
import edu.eci.dosw.campusseguro.audit.security.GatewayAuthenticationFilter;
import edu.eci.dosw.campusseguro.audit.security.RestAccessDeniedHandler;
import edu.eci.dosw.campusseguro.audit.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * MS08 runs behind the API Gateway: it trusts the identity headers the Gateway injects.
 * No JWT and no CORS here (the Gateway does both). Endpoint permissions are added with each
 * endpoint; by default every route needs an identity.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            GatewayAuthenticationFilter gatewayFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error", "/actuator/health").permitAll()
                        // Internal route: only other microservices (X-Service-Name) may send facts.
                        .requestMatchers(HttpMethod.POST, "/api/v1/audit/events")
                        .hasAuthority(Authorities.SERVICE)
                        // Reading needs one of the two audit permissions; the service narrows the result
                        // to the kind each permission allows.
                        .requestMatchers(HttpMethod.GET, "/api/v1/audit/events", "/api/v1/audit/events/*")
                        .hasAnyAuthority(Authorities.AUDIT_DRILL_READ, Authorities.AUDIT_REAL_EVENT_READ)
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(gatewayFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
