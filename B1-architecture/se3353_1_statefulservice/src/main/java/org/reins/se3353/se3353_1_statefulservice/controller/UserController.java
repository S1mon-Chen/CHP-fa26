package org.reins.se3353.se3353_1_statefulservice.controller;

import org.reins.se3353.se3353_1_statefulservice.entity.User;
import org.reins.se3353.se3353_1_statefulservice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;

@RestController
//@Scope("prototype")
public class UserController {

    @Autowired
    WebApplicationContext applicationContext;

    @GetMapping(value = "/findUser/{id}")
    public User findOne(@PathVariable("id") String id) {
        UserService userService = applicationContext.getBean(UserService.class);
        System.out.println(userService);
        System.out.println(this);
        return userService.findUserById(Integer.valueOf(id));
    };
}
