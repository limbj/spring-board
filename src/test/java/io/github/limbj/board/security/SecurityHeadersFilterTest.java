package io.github.limbj.board.security;

import static org.junit.Assert.*;
import org.junit.Test;
import org.springframework.mock.web.*;

public class SecurityHeadersFilterTest {
    @Test public void emitsCspNonceAndBrowserProtections() throws Exception {
        MockHttpServletRequest request=new MockHttpServletRequest(); MockHttpServletResponse response=new MockHttpServletResponse();
        new SecurityHeadersFilter().doFilter(request,response,(req,res)->{});
        String nonce=(String)request.getAttribute("cspNonce");
        assertNotNull(nonce);
        String policy=response.getHeader("Content-Security-Policy");
        assertTrue(policy.contains("script-src 'self' 'nonce-"+nonce+"'"));
        assertTrue(policy.contains("object-src 'none'")); assertTrue(policy.contains("frame-ancestors 'none'"));
        assertEquals("nosniff",response.getHeader("X-Content-Type-Options"));
        assertEquals("DENY",response.getHeader("X-Frame-Options"));
        assertEquals("same-origin",response.getHeader("Referrer-Policy"));
    }
    @Test public void nonceIsUniqueForEveryResponse() throws Exception {
        SecurityHeadersFilter filter=new SecurityHeadersFilter();
        MockHttpServletRequest first=new MockHttpServletRequest(),second=new MockHttpServletRequest();
        filter.doFilter(first,new MockHttpServletResponse(),(req,res)->{});
        filter.doFilter(second,new MockHttpServletResponse(),(req,res)->{});
        assertNotEquals(first.getAttribute("cspNonce"),second.getAttribute("cspNonce"));
    }
}