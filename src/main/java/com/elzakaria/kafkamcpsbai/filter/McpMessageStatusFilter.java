package com.elzakaria.kafkamcpsbai.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Change the 200 to ACCEPTED as spring boot ai doesnt support claude sse format yet.
 */
@Component
public class McpMessageStatusFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if ("POST".equals(httpRequest.getMethod()) && "/mcp/message".equals(httpRequest.getRequestURI())) {
            chain.doFilter(request, new HttpServletResponseWrapper((HttpServletResponse) response) {
                @Override
                public void setStatus(int sc) {
                    super.setStatus(sc == HttpServletResponse.SC_OK ? HttpServletResponse.SC_ACCEPTED : sc);
                }
            });
        } else {
            chain.doFilter(request, response);
        }
    }
}
