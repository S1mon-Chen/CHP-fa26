package com.bookstore.tx.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.apache.seata.core.context.RootContext;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Explicit HTTP RPC transaction propagation; always clear the pooled servlet thread. */
@Component
public class XidFilter extends OncePerRequestFilter {
    private final ObjectMapper mapper;
    public XidFilter(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/internal/")) {
            chain.doFilter(request, response);
            return;
        }
        String xid = request.getHeader(RootContext.KEY_XID);
        if (!"GET".equals(request.getMethod()) && (xid == null || xid.isBlank())) {
            response.setStatus(400);
            response.setContentType("application/json;charset=UTF-8");
            mapper.writeValue(response.getWriter(), new Contracts.ApiError("XID_REQUIRED", "分支写入必须属于全局事务", null, null));
            return;
        }
        String previous = RootContext.getXID();
        try {
            if (xid != null && !xid.isBlank()) RootContext.bind(xid);
            chain.doFilter(request, response);
        } finally {
            RootContext.unbind();
            if (previous != null) RootContext.bind(previous);
        }
    }
}
