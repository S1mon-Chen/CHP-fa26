package com.bookstore.agent.context;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class AgentContext implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String conversationId;
    private Object selectedBook;
    private OrderInfo orderInfo;
    private Map<String, Object> preferences = new HashMap<>();
    private List<ExecutionRecord> executionHistory = new LinkedList<>();
    private Map<String, Object> data = new HashMap<>();
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public AgentContext() {
        this.preferences = new HashMap<>();
        this.executionHistory = new LinkedList<>();
        this.data = new HashMap<>();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Object getSelectedBook() { return selectedBook; }
    public void setSelectedBook(Object selectedBook) { this.selectedBook = selectedBook; }
    public OrderInfo getOrderInfo() { return orderInfo; }
    public void setOrderInfo(OrderInfo orderInfo) { this.orderInfo = orderInfo; }
    public Map<String, Object> getPreferences() { return preferences; }
    public void setPreferences(Map<String, Object> preferences) { this.preferences = preferences != null ? preferences : new HashMap<>(); }
    public List<ExecutionRecord> getExecutionHistory() { return executionHistory; }
    public void setExecutionHistory(List<ExecutionRecord> executionHistory) { this.executionHistory = executionHistory != null ? executionHistory : new LinkedList<>(); }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data != null ? data : new HashMap<>(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public void addExecutionRecord(String agentName, String input, String result, boolean success) {
        if (executionHistory == null) executionHistory = new LinkedList<>();
        executionHistory.add(new ExecutionRecord(agentName, input, result, success, LocalDateTime.now()));
    }

    public ExecutionRecord getLastExecutionRecord() {
        return (executionHistory == null || executionHistory.isEmpty()) ? null : executionHistory.get(executionHistory.size() - 1);
    }

    public void setData(String key, Object value) {
        if (data == null) data = new HashMap<>();
        data.put(key, value);
        this.updatedAt = LocalDateTime.now();
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(String key, Class<T> clazz) {
        if (data == null) return null;
        Object value = data.get(key);
        return (value != null && clazz.isAssignableFrom(value.getClass())) ? (T) value : null;
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(String key, T defaultValue) {
        if (data == null) return defaultValue;
        Object value = data.get(key);
        if (value != null) {
            try { return (T) value; } catch (ClassCastException e) { return defaultValue; }
        }
        return defaultValue;
    }

    public boolean isLoggedIn() { return userId != null; }

    public void clear() {
        this.selectedBook = null;
        this.orderInfo = null;
        this.preferences = new HashMap<>();
        this.executionHistory = new LinkedList<>();
        this.data = new HashMap<>();
        this.updatedAt = LocalDateTime.now();
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final AgentContext context = new AgentContext();

        public Builder userId(Long userId) { context.userId = userId; return this; }
        public Builder username(String username) { context.username = username; return this; }
        public Builder conversationId(String conversationId) { context.conversationId = conversationId; return this; }
        public Builder selectedBook(Object selectedBook) { context.selectedBook = selectedBook; return this; }
        public Builder orderInfo(OrderInfo orderInfo) { context.orderInfo = orderInfo; return this; }
        public Builder preferences(Map<String, Object> preferences) { context.preferences = preferences; return this; }
        public Builder executionHistory(List<ExecutionRecord> executionHistory) { context.executionHistory = executionHistory; return this; }
        public Builder data(Map<String, Object> data) { context.data = data; return this; }
        public Builder createdAt(LocalDateTime createdAt) { context.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { context.updatedAt = updatedAt; return this; }

        public AgentContext build() { return context; }
    }

    public static class OrderInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private Long orderId;
        private BigDecimal totalAmount;
        private BigDecimal finalAmount;
        private BigDecimal discountAmount;
        private String discountType;
        private String status;

        public OrderInfo() {}

        public OrderInfo(Long orderId, BigDecimal totalAmount, BigDecimal finalAmount, BigDecimal discountAmount, String discountType, String status) {
            this.orderId = orderId;
            this.totalAmount = totalAmount;
            this.finalAmount = finalAmount;
            this.discountAmount = discountAmount;
            this.discountType = discountType;
            this.status = status;
        }

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public BigDecimal getFinalAmount() { return finalAmount; }
        public void setFinalAmount(BigDecimal finalAmount) { this.finalAmount = finalAmount; }
        public BigDecimal getDiscountAmount() { return discountAmount; }
        public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
        public String getDiscountType() { return discountType; }
        public void setDiscountType(String discountType) { this.discountType = discountType; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class ExecutionRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String agentName;
        private String input;
        private String result;
        private boolean success;
        private LocalDateTime timestamp;

        public ExecutionRecord() {}

        public ExecutionRecord(String agentName, String input, String result, boolean success, LocalDateTime timestamp) {
            this.agentName = agentName;
            this.input = input;
            this.result = result;
            this.success = success;
            this.timestamp = timestamp;
        }

        public String getAgentName() { return agentName; }
        public void setAgentName(String agentName) { this.agentName = agentName; }
        public String getInput() { return input; }
        public void setInput(String input) { this.input = input; }
        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
}