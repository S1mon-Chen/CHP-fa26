package org.reins.se3353.se3353_9_springrestfulws.controller;

import org.reins.se3353.se3353_9_springrestfulws.entity.Person;
import org.reins.se3353.se3353_9_springrestfulws.service.PersonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/persons")
public class PersonController {

    @Autowired
    private PersonService personService;

    // build create Person REST API
    @PostMapping
    public ResponseEntity<Person> createPerson(@RequestBody Person person){
        Person savedPerson = personService.createPerson(person);
        return new ResponseEntity<>(savedPerson, HttpStatus.CREATED);
    }

    // build get person by id REST API
    // http://localhost:8080/api/persons/1
    @GetMapping("{id}")
    public ResponseEntity<Person> getUserById(@PathVariable("id") Long personId){
        Person person = personService.getPersonById(personId);
        return new ResponseEntity<>(person, HttpStatus.OK);
    }

    // Build Get All Persons REST API
    // http://localhost:8080/api/persons
    @GetMapping
    public ResponseEntity<List<Person>> getAllPersons(){
        List<Person> persons = personService.getAllPersons();
        return new ResponseEntity<>(persons, HttpStatus.OK);
    }

    // Build Update Person REST API
    @PutMapping("{id}")
    // http://localhost:8080/api/persons/1
    public ResponseEntity<Person> updatePerson(@PathVariable("id") Long personId,
                                           @RequestBody Person person){
        person.setPersonId(Math.toIntExact(personId));
        Person updatedPerson = personService.updatePerson(person);
        return new ResponseEntity<>(updatedPerson, HttpStatus.OK);
    }

    // Build Delete Person REST API
    @DeleteMapping("{id}")
    public ResponseEntity<String> deletePerson(@PathVariable("id") Long personId){
        personService.deletePerson(personId);
        return new ResponseEntity<>("Person successfully deleted!", HttpStatus.OK);
    }

}
