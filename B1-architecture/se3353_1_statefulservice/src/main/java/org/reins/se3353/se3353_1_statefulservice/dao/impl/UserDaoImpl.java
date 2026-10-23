package org.reins.se3353.se3353_1_statefulservice.dao.impl;

import org.reins.se3353.se3353_1_statefulservice.dao.UserDao;
import org.reins.se3353.se3353_1_statefulservice.entity.User;
import org.reins.se3353.se3353_1_statefulservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class UserDaoImpl implements UserDao {

    @Autowired
    private UserRepository userRepository;

    @Override
    public User findOne(Integer id) {
        return userRepository.getOne(id);
    }
}
