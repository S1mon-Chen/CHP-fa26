package com.bookstore.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/** Verifies JWTs and resolves current database roles/status without storing login sessions. */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserDetailsService users;
    private final JwtAuthenticationEntryPoint unauthorized;

    public JwtAuthenticationFilter(JwtService jwt, UserDetailsService users,
                                   JwtAuthenticationEntryPoint unauthorized) {
        this.jwt = jwt;
        this.users = users;
        this.unauthorized = unauthorized;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        try {
            String header = headers.getFirst();
            if (headers.size() != 1 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                throw new BadCredentialsException("Expected one Bearer header");
            }
            var user = users.loadUserByUsername(jwt.verify(header.substring(7)));
            if (!user.isEnabled() || !user.isAccountNonLocked() || !user.isAccountNonExpired()
                    || !user.isCredentialsNonExpired()) {
                throw new BadCredentialsException("Account unavailable");
            }
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    user.getUsername(), null, user.getAuthorities()));
            SecurityContextHolder.setContext(context);
        } catch (JwtException | IllegalArgumentException | AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            unauthorized.commence(request, response, new BadCredentialsException("Invalid token"));
            return;
        }
        chain.doFilter(request, response);
    }
}
