package org.reins.se3353.auth.util;

import lombok.Data;

@Data
public class Response {
    private Integer code;
    private Object data;
    private String message;
}
