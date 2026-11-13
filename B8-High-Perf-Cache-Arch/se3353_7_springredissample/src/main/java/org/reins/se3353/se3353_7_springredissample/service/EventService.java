package org.reins.se3353.se3353_7_springredissample.service;

import org.reins.se3353.se3353_7_springredissample.entity.Event;
/**
 * Created by chenhaopeng on 2019/5/2.
 */
public interface EventService {
    Event findEventById(Integer id);
}
