package es.idynamicsax.ledger.security;

import es.idynamicsax.idax.security.CurrentUser;
import es.idynamicsax.idax.security.TokenValidator;
import es.idynamicsax.idax.tenant.TenantContext;
import es.idynamicsax.idax.tenant.TenantResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class LedgerJwtAuthFilter extends OncePerRequestFilter {
    private final TokenValidator tokenValidator;
    private final TenantResolver tenantResolver;

    public LedgerJwtAuthFilter(TokenValidator tokenValidator, TenantResolver tenantResolver) {
        this.tokenValidator = tokenValidator;
        this.tenantResolver = tenantResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                var result = tokenValidator.validateAndExtract(authorization.substring(7));
                if (result.isValid()) {
                    CurrentUser user = result.getCurrentUser();
                    if (user.isService() && request.getHeader("X-Tenant") != null) {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                                "X-Tenant is not allowed for service principals");
                        return;
                    }
                    UUID tenantId = resolveTenant(request, user);
                    var authorities = user.getRoles().stream().map(SimpleGrantedAuthority::new).toList();
                    var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    TenantContext.set(new TenantContext(
                            tenantId, null, user.getUserId(), user.getUsername(),
                            user.isSuperuser() ? TenantContext.DbRole.IDAX_ADMIN : TenantContext.DbRole.IDAX_APP));
                }
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private UUID resolveTenant(HttpServletRequest request, CurrentUser user) {
        String tenantHeader = request.getHeader("X-Tenant");
        if (tenantHeader == null || tenantHeader.isBlank()) {
            return user.getTenantId();
        }
        UUID tenantId = tenantResolver.resolveByHeader(tenantHeader)
                .map(TenantResolver.ResolvedTenant::tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant"));
        if (!user.isSuperuser() && !tenantId.equals(user.getTenantId())) {
            throw new IllegalArgumentException("Tenant access denied");
        }
        return tenantId;
    }
}
