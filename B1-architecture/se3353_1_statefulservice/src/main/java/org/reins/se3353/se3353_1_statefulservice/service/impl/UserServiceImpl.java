package org.reins.se3353.se3353_1_statefulservice.service.impl;

import org.reins.se3353.se3353_1_statefulservice.dao.UserDao;
import org.reins.se3353.se3353_1_statefulservice.entity.User;
import org.reins.se3353.se3353_1_statefulservice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope("session")
public class UserServiceImpl implements UserService {
    @Autowired
    private UserDao userDao;

    @Override
    public User findUserById(Integer id) {
        return userDao.findOne(id);
    }
}
