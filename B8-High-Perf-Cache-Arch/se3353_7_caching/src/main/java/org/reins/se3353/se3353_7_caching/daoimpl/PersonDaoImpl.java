package org.reins.se3353.se3353_7_caching.daoimpl;

import org.springframework.cache.annotation.Cacheable;
import org.reins.se3353.se3353_7_caching.dao.PersonDao;
import org.reins.se3353.se3353_7_caching.entity.Person;
import org.reins.se3353.se3353_7_caching.repository.PersonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class PersonDaoImpl implements PersonDao {
    @Autowired
    private PersonRepository personRepository;

    @Override
//    @Cacheable("books")
    public Person findOne(Integer id) {
        simulateSlowService();
        return personRepository.getOne(id);
    }

    private void simulateSlowService() {
        try {
            long time = 3000L;
            Thread.sleep(time);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
