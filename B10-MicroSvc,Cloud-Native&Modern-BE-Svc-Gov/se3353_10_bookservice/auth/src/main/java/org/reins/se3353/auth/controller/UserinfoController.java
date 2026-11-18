package org.reins.se3353.auth.controller;

import org.reins.se3353.auth.bean.Userinfo;
import org.reins.se3353.auth.service.UserinfoService;
import org.reins.se3353.auth.util.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class UserinfoController {

    @Autowired
    UserinfoService userinfoService;

    @ResponseBody
    @PostMapping("/register")
    public Response register(Userinfo userinfo){
        return userinfoService.insert(userinfo);
    }

    @ResponseBody
    @PostMapping("/Login")
    public Response login(Userinfo userinfo){
        System.out.println("userInfo " + userinfo.toString());
        return  userinfoService.verify(userinfo);
    }

    @PostMapping("/refresh-token")
    public Response refreshToken(@RequestBody Map<String, String> refreshToken){
        return userinfoService.refreshToken(refreshToken.get("refreshToken"));
    }

}
