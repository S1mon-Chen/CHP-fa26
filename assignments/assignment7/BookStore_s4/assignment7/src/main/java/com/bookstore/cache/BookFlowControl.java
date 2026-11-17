package com.bookstore.cache;

import java.util.List;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BookFlowControl {
    // All book IDs share one resource; changing an ID cannot bypass the limit.
    public static final String RESOURCE = "GET:/api/books/{id}";
    public BookFlowControl(@Value("${book.flow.qps}") double qps) {
        if (qps <= 0) throw new IllegalArgumentException("QPS must be positive");
        FlowRule rule = new FlowRule(RESOURCE);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(qps);
        rule.setLimitApp("default");
        rule.setStrategy(RuleConstant.STRATEGY_DIRECT);
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        FlowRuleManager.loadRules(List.of(rule));
    }
    // Serialize only admission to avoid a concurrent check/increment overshoot.
    // Redis/SQL and response handling remain concurrent; Sentinel owns the counters.
    public synchronized Entry enter() throws BlockException {
        return SphU.entry(RESOURCE, EntryType.IN);
    }
}
