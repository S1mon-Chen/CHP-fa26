package org.reins.se3353.se3353_9_springrestfulws.repository;

import org.reins.se3353.se3353_9_springrestfulws.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Created by chenhaopeng on 2019/5/2.
 */
public interface PersonRepository extends JpaRepository<Person, Integer>{

}

