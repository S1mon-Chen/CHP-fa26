package com.bookstore.agent.communication;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 智能体通信消息类
 * 用于智能体之间的消息传递
 */
public class AgentMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
    private String messageId = java.util.UUID.randomUUID().toString();

    /**
     * 发送方智能体名称
     */
    private String sender;

    /**
     * 接收方智能体名称（为空表示广播）
     */
    private String receiver;

    /**
     * 消息类型
     */
    private MessageType type;

    /**
     * 消息内容
     */
    private Object content;

    /**
     * 消息元数据
     */
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * 对话ID（用于关联会话）
     */
    private String conversationId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 消息时间戳
     */
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 消息优先级
     */
    private Priority priority = Priority.NORMAL;

    /**
     * 是否需要回复
     */
    private boolean requiresReply = false;

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        REQUEST, RESPONSE, NOTIFICATION, ERROR, HEARTBEAT, STATUS_UPDATE
    }

    /**
     * 消息优先级枚举
     */
    public enum Priority {
        LOW, NORMAL, HIGH, URGENT
    }

    public AgentMessage() {
        this.messageId = java.util.UUID.randomUUID().toString();
        this.metadata = new HashMap<>();
        this.timestamp = LocalDateTime.now();
        this.priority = Priority.NORMAL;
    }

    public AgentMessage(String messageId, String sender, String receiver, MessageType type,
                       Object content, Map<String, Object> metadata, String conversationId,
                       Long userId, LocalDateTime timestamp, Priority priority, boolean requiresReply) {
        this.messageId = messageId != null ? messageId : java.util.UUID.randomUUID().toString();
        this.sender = sender;
        this.receiver = receiver;
        this.type = type;
        this.content = content;
        this.metadata = metadata != null ? metadata : new HashMap<>();
        this.conversationId = conversationId;
        this.userId = userId;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.priority = priority != null ? priority : Priority.NORMAL;
        this.requiresReply = requiresReply;
    }

    /**
     * 创建请求消息
     */
    public static AgentMessage request(String sender, String receiver, Object content, String conversationId) {
        AgentMessage msg = new AgentMessage();
        msg.setSender(sender);
        msg.setReceiver(receiver);
        msg.setType(MessageType.REQUEST);
        msg.setContent(content);
        msg.setConversationId(conversationId);
        msg.setRequiresReply(true);
        msg.setPriority(Priority.NORMAL);
        return msg;
    }

    /**
     * 创建响应消息
     */
    public static AgentMessage response(String sender, String receiver, Object content, String conversationId) {
        AgentMessage msg = new AgentMessage();
        msg.setSender(sender);
        msg.setReceiver(receiver);
        msg.setType(MessageType.RESPONSE);
        msg.setContent(content);
        msg.setConversationId(conversationId);
        msg.setRequiresReply(false);
        msg.setPriority(Priority.NORMAL);
        return msg;
    }

    /**
     * 创建通知消息
     */
    public static AgentMessage notify(String sender, Object content, String conversationId) {
        AgentMessage msg = new AgentMessage();
        msg.setSender(sender);
        msg.setReceiver(null);
        msg.setType(MessageType.NOTIFICATION);
        msg.setContent(content);
        msg.setConversationId(conversationId);
        msg.setRequiresReply(false);
        msg.setPriority(Priority.LOW);
        return msg;
    }

    /**
     * 创建错误消息
     */
    public static AgentMessage error(String sender, String receiver, String errorMessage, String conversationId) {
        AgentMessage msg = new AgentMessage();
        msg.setSender(sender);
        msg.setReceiver(receiver);
        msg.setType(MessageType.ERROR);
        msg.setContent(errorMessage);
        msg.setConversationId(conversationId);
        msg.setRequiresReply(false);
        msg.setPriority(Priority.HIGH);
        return msg;
    }

    /**
     * 设置元数据
     */
    public AgentMessage setMetadata(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
        return this;
    }

    /**
     * 获取元数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key, Class<T> clazz) {
        if (this.metadata == null) {
            return null;
        }
        Object value = this.metadata.get(key);
        if (value != null && clazz.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }

    // Getter and Setter methods
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    public Object getContent() { return content; }
    public void setContent(Object content) { this.content = content; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public boolean isRequiresReply() { return requiresReply; }
    public void setRequiresReply(boolean requiresReply) { this.requiresReply = requiresReply; }
}