package org.reins.se3353.se3353_7_springredissample.repository;

import org.reins.se3353.se3353_7_springredissample.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Created by chenhaopeng on 2019/5/2.
 */
public interface EventRepository extends JpaRepository<Event, Integer>{

}

