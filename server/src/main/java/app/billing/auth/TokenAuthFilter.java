package app.billing.auth;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import app.billing.channel.ChannelFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Bearer-token auth. Tokens (not cookies) on purpose: the admin app and the billing website run
 * on different ports of the same host, and cookies are shared across ports — tokens in each
 * app's own localStorage are not.
 */
public class TokenAuthFilter extends OncePerRequestFilter {

    private final AuthService auth;

    public TokenAuthFilter(AuthService auth) {
        this.auth = auth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String raw = header.substring(7).trim();
            auth.authenticate(raw, ChannelFilter.channelOf(req)).ifPresent(cu -> {
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + cu.role().name()));
                var authentication = new UsernamePasswordAuthenticationToken(cu, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        chain.doFilter(req, res);
    }
}
