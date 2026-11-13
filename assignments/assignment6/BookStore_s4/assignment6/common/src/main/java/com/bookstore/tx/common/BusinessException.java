package com.bookstore.tx.common;

public class BusinessException extends RuntimeException {
    private final int status;
    private final String code;
    private final String xid;
    private final String requestId;

    public BusinessException(int status, String code, String message, String xid, String requestId) {
        super(message);
        this.status = status;
        this.code = code;
        this.xid = xid;
        this.requestId = requestId;
    }
    public int status() { return status; }
    public String code() { return code; }
    public String xid() { return xid; }
    public String requestId() { return requestId; }
}
