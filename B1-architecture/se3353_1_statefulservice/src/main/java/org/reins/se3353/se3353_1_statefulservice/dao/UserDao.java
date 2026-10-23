package org.reins.se3353.se3353_1_statefulservice.dao;

import org.reins.se3353.se3353_1_statefulservice.entity.User;

public interface UserDao {
    User findOne(Integer id);
}
