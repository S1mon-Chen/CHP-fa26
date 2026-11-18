package org.reins.se3353.auth.mapper;

import org.reins.se3353.auth.bean.Userinfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserinfoMapper extends JpaRepository<Userinfo, Integer> {

    Userinfo findUserinfoByUsername(String username);

}