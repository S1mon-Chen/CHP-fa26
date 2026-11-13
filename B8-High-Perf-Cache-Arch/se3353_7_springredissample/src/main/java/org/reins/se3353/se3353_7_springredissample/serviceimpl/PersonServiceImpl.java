package org.reins.se3353.se3353_7_springredissample.serviceimpl;

import org.reins.se3353.se3353_7_springredissample.dao.PersonDao;
import org.reins.se3353.se3353_7_springredissample.entity.Person;
import org.reins.se3353.se3353_7_springredissample.service.PersonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Created by chenhaopeng on 2019/5/2.
 */
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
