package io.github.limbj.board.security;

import static org.junit.Assert.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.springframework.mock.web.*;

public class CsrfFilterTest {
    private final CsrfFilter filter=new CsrfFilter();
    @Test public void getCreatesStableSessionTokenAndRequestAttribute() throws Exception {
        MockHttpSession session=new MockHttpSession();
        MockHttpServletRequest first=request("GET",session); pass(first);
        String token=(String)session.getAttribute("csrfToken");
        assertEquals(43,token.length()); assertEquals(token,first.getAttribute("csrfToken"));
        MockHttpServletRequest second=request("GET",session); pass(second);
        assertEquals(token,second.getAttribute("csrfToken"));
    }
    @Test public void unsafeMethodsRejectMissingAndWrongTokens() throws Exception {
        for(String method:new String[]{"POST","PUT","PATCH","DELETE"}) {
            MockHttpSession session=new MockHttpSession(); pass(request("GET",session));
            reject(request(method,session));
            MockHttpServletRequest wrong=request(method,session); wrong.addHeader("X-CSRF-Token","wrong"); reject(wrong);
        }
    }
    @Test public void headerSupportsAjaxAndParameterSupportsForms() throws Exception {
        MockHttpSession session=new MockHttpSession(); pass(request("GET",session));
        String token=(String)session.getAttribute("csrfToken");
        MockHttpServletRequest json=request("PATCH",session); json.addHeader("X-CSRF-Token",token); pass(json);
        MockHttpServletRequest form=request("POST",session); form.addParameter("_csrf",token); pass(form);
    }
    @Test public void differentSessionTokenAndDuplicateParametersAreRejected() throws Exception {
        MockHttpSession owner=new MockHttpSession(); pass(request("GET",owner));
        String token=(String)owner.getAttribute("csrfToken");
        MockHttpServletRequest foreign=request("POST",new MockHttpSession()); foreign.addParameter("_csrf",token); reject(foreign);
        MockHttpServletRequest duplicate=request("POST",owner); duplicate.addParameter("_csrf",token,token); reject(duplicate);
    }
    @Test public void safeMethodsContinueWithoutSuppliedToken() throws Exception {
        pass(request("HEAD",new MockHttpSession())); pass(request("OPTIONS",new MockHttpSession()));
    }
    private static MockHttpServletRequest request(String method,MockHttpSession session) {
        MockHttpServletRequest request=new MockHttpServletRequest(method,"/board/register"); request.setSession(session); return request;
    }
    private void pass(MockHttpServletRequest request) throws Exception {
        AtomicBoolean invoked=new AtomicBoolean(); MockHttpServletResponse response=new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->invoked.set(true)); assertTrue(invoked.get()); assertEquals(200,response.getStatus());
    }
    private void reject(MockHttpServletRequest request) throws Exception {
        AtomicBoolean invoked=new AtomicBoolean(); MockHttpServletResponse response=new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->invoked.set(true)); assertFalse(invoked.get()); assertEquals(403,response.getStatus());
    }
}