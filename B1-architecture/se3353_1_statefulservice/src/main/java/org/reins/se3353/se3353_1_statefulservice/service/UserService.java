package org.reins.se3353.se3353_1_statefulservice.service;

import org.reins.se3353.se3353_1_statefulservice.entity.User;

public interface UserService {
    User findUserById(Integer id);
}
