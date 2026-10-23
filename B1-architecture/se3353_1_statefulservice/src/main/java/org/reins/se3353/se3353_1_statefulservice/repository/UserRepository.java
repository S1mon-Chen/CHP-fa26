package org.reins.se3353.se3353_1_statefulservice.repository;

import org.reins.se3353.se3353_1_statefulservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User,Integer> {
}
