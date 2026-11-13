package org.reins.se3353.se3353_7_caching.serviceimpl;

import org.reins.se3353.se3353_7_caching.dao.PersonDao;
import org.reins.se3353.se3353_7_caching.entity.Person;
import org.reins.se3353.se3353_7_caching.service.PersonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
public class PersonServiceImpl implements PersonService {

    @Autowired
    private PersonDao personDao;

    @Override
    public Person findPersonById(Integer id){
        return personDao.findOne(id);
    }
}
