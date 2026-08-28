package es.idynamicsax.ledger.config;

import es.idynamicsax.idax.tenant.AppUserResolver;
import es.idynamicsax.idax.tenant.TenantContextFilter;
import es.idynamicsax.idax.tenant.TenantResolver;
import es.idynamicsax.ledger.security.LedgerJwtAuthFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class LedgerSecurityConfig {
    @Bean
    SecurityFilterChain ledgerSecurityFilterChain(
            HttpSecurity http, LedgerJwtAuthFilter jwtFilter, TenantContextFilter tenantFilter) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, cause) -> response.sendError(HttpStatus.UNAUTHORIZED.value())))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(tenantFilter, LedgerJwtAuthFilter.class)
                .build();
    }

    @Bean
    TenantContextFilter tenantContextFilter(TenantResolver tenantResolver, AppUserResolver appUserResolver) {
        return new TenantContextFilter(tenantResolver, appUserResolver);
    }
}
