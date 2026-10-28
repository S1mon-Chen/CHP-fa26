package org.reins.se3353.se3353_9_springrestfulws.serviceimpl;

import org.reins.se3353.se3353_9_springrestfulws.entity.Person;
import org.reins.se3353.se3353_9_springrestfulws.repository.PersonRepository;
import org.reins.se3353.se3353_9_springrestfulws.service.PersonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Created by chenhaopeng on 2019/5/2.
 */
@Transactional
@Service
public class PersonServiceImpl implements PersonService {

    @Autowired
    PersonRepository personRepository;

    @Override
    public Person createPerson(Person person) {
        return personRepository.save(person);
    }

    @Override
    public Person getPersonById(Long personId) {
        Optional<Person> optionalPerson = personRepository.findById(Math.toIntExact(personId));
        return optionalPerson.get();
    }

    @Override
    public List<Person> getAllPersons() {
        return personRepository.findAll();
    }

    @Override
    public Person updatePerson(Person person) {
        Person existingPerson = personRepository.findById(person.getPersonId()).get();
        existingPerson.setFirstname(person.getFirstname());
        existingPerson.setLastname(person.getLastname());
        existingPerson.setAge(person.getAge());
        Person updatedPerson = personRepository.save(existingPerson);
        return updatedPerson;
    }

    @Override
    public void deletePerson(Long personId) {
        personRepository.deleteById(Math.toIntExact(personId));
    }
}
