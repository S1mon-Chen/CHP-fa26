package org.reins.se3353.se3353_7_springredissample.service;

import org.reins.se3353.se3353_7_springredissample.entity.Person;

/**
 * Created by chenhaopeng on 2019/5/2.
 */
public interface PersonService {
    Person findPersonById(Integer id);
}
