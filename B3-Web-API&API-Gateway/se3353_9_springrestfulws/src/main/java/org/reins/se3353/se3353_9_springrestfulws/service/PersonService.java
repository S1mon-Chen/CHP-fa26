package org.reins.se3353.se3353_9_springrestfulws.service;

import org.reins.se3353.se3353_9_springrestfulws.entity.Person;

import java.util.List;

/**
 * Created by chenhaopeng on 2019/5/2.
 */
public interface PersonService {

    Person createPerson(Person person);

    Person getPersonById(Long personId);

    List<Person> getAllPersons();

    Person updatePerson(Person person);

    void deletePerson(Long personId);
}
