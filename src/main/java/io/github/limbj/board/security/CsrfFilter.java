package io.github.limbj.board.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

public class CsrfFilter implements Filter {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest req) || !(response instanceof HttpServletResponse res)) {
            chain.doFilter(request, response);
            return;
        }
        HttpSession session = req.getSession();
        String expected;
        synchronized (session) {
            Object existing = session.getAttribute("csrfToken");
            if (existing instanceof String token && token.length() == 43) expected = token;
            else {
                byte[] random = new byte[32];
                RANDOM.nextBytes(random);
                expected = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
                session.setAttribute("csrfToken", expected);
            }
        }
        req.setAttribute("csrfToken", expected);
        if (!SAFE_METHODS.contains(req.getMethod().toUpperCase(java.util.Locale.ROOT))) {
            String supplied = req.getHeader("X-CSRF-Token");
            if (supplied == null || supplied.isEmpty()) {
                String[] parameters = req.getParameterValues("_csrf");
                supplied = parameters != null && parameters.length == 1 ? parameters[0] : null;
            }
            if (supplied == null || supplied.length() != expected.length()
                    || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII))) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}