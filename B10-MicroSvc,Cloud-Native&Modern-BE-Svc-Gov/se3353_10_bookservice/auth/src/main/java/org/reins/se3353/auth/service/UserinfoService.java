package org.reins.se3353.auth.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.reins.se3353.auth.bean.Userinfo;
import org.reins.se3353.auth.mapper.UserinfoMapper;
import org.reins.se3353.auth.util.JWTUtils;
import org.reins.se3353.auth.util.Response;
import org.reins.se3353.auth.util.TokenPair;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.Objects;


@Service
public class UserinfoService {
    @Autowired
    UserinfoMapper userinfoMapper;
    /*
    检测 username 是否已经存在
     */
    private boolean usernameExist(String username){
        Userinfo userinfo = userinfoMapper.findUserinfoByUsername(username);
        return userinfo != null;
    }

    public Response insert(Userinfo userinfo){
        Response response = new Response();

        // 如果 username 已经存在，则返回 error message
        if(usernameExist(userinfo.getUsername())){
            response.setCode(201);
            response.setMessage("Username existed!");
        }
        else{
            userinfo.setUserRole("USER");
            userinfoMapper.save(userinfo);
            response.setCode(200);
            response.setMessage("success");
        }

        return response;
    }

    public TokenPair createTokenPair(Userinfo userinfo){
        String token, refreshToken;
        // token
        token = JWTUtils.createToken(userinfo, false);
        // refreshToken
        refreshToken = JWTUtils.createToken(userinfo, true);
        return new TokenPair(token, refreshToken);
    }


    public Response verify(Userinfo userinfo) {
        Response response = new Response();

        if(usernameExist(userinfo.getUsername())){
            Userinfo _userinfo = userinfoMapper.findUserinfoByUsername(userinfo.getUsername());

            if(Objects.equals(userinfo.getPassword(), _userinfo.getPassword())) {
                response.setCode(200);
                response.setMessage("success");
                response.setData(createTokenPair(_userinfo));
            }
            else{
                response.setCode(201);
                response.setMessage("Wrong username or password");
            }
        }
        else{
            response.setCode(201);
            response.setMessage("Wrong username or password");
        }
        return  response;
    }

    public Response refreshToken(String refreshToken) {
        Response response = new Response();
        String username = null;
        try{
            DecodedJWT decodedJWT = JWT.decode(refreshToken);
            username = decodedJWT.getAudience().get(0);
        }
        catch (JWTDecodeException jwtDecodeException) {
            response.setCode(201);
            response.setData("");
            response.setMessage("RefreshToken is invalid!");
            return response;
        }

        if(usernameExist(username)){
            Userinfo userinfo = userinfoMapper.findUserinfoByUsername(username);
            response.setCode(200);
            response.setData(createTokenPair(userinfo));
            response.setMessage("success");

        }
        else{
            response.setCode(201);
            response.setData("");
            response.setMessage("RefreshToken is invalid!");
        }
        return response;
    }

}
