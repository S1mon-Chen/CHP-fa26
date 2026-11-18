package org.reins.se3353.auth.util;

import lombok.Data;

@Data
public class TokenPair {
    public TokenPair(String _token, String _refreshToken){
        token = _token;
        refreshToken = _refreshToken;
    }
    String token;
    String refreshToken;
}
