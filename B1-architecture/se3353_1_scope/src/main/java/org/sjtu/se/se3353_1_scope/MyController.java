package org.sjtu.se.se3353_1_scope;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MyController {

    @Autowired
    private RequestContext requestContext;

    @GetMapping("/demo")
    public String demo() throws InterruptedException {
        Thread.sleep(100);
        return "Trace ID: " + requestContext.getTraceId();
    }
}

