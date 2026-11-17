package com.bookstore.tx.checkout;

import feign.RequestInterceptor;
import feign.Retryer;
import org.apache.seata.core.context.RootContext;
import org.springframework.context.annotation.*;

@Configuration
public class RpcConfiguration {
    @Bean RequestInterceptor xidPropagation() {
        return template -> {
            String xid = RootContext.getXID();
            if (xid != null) template.header(RootContext.KEY_XID, xid);
        };
    }
    // Do not transparently replay a non-idempotent deduction after an uncertain HTTP outcome.
    @Bean Retryer retryer() { return Retryer.NEVER_RETRY; }
}
