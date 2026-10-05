package io.github.limbj.board.security;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

public class SecurityHeadersFilter implements Filter {
    private static final SecureRandom RANDOM = new SecureRandom();
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest req && response instanceof HttpServletResponse res) {
            byte[] random = new byte[16];
            RANDOM.nextBytes(random);
            String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            req.setAttribute("cspNonce", nonce);
            res.setHeader("X-Content-Type-Options", "nosniff");
            res.setHeader("X-Frame-Options", "DENY");
            res.setHeader("Referrer-Policy", "same-origin");
            res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            res.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self' 'nonce-" + nonce
                    + "'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; "
                    + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'");
        }
        chain.doFilter(request, response);
    }
}