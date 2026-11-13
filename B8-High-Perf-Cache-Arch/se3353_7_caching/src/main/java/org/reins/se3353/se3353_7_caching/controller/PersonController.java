package org.reins.se3353.se3353_7_caching.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.reins.se3353.se3353_7_caching.entity.Person;
import org.reins.se3353.se3353_7_caching.service.PersonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PersonController {

    private static final Logger logger = LoggerFactory.getLogger(PersonController.class);

    @Autowired
    private PersonService personService;

    @GetMapping(value = "/findPerson/{id}")
    public Person findPerson(@PathVariable("id") String id) {
        logger.info(".... Fetching books");
        System.out.println("Searching Person: " + id);
        Person person = personService.findPersonById(Integer.valueOf(id));
        logger.info(".... Got books");
        return person;
    }
}
