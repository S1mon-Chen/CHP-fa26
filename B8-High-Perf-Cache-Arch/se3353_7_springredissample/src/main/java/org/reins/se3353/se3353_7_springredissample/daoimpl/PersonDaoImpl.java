package org.reins.se3353.se3353_7_springredissample.daoimpl;


import com.alibaba.fastjson2.JSON;
import org.reins.se3353.se3353_7_springredissample.dao.PersonDao;
import org.reins.se3353.se3353_7_springredissample.entity.Person;
import org.reins.se3353.se3353_7_springredissample.repository.PersonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PersonDaoImpl implements PersonDao {
    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public Person findOne(Integer id) {
        Person person = null;
        System.out.println("Searching Person: " + id + " in Redis");

        String p = (String)redisTemplate.opsForValue().get("user" + id);

        if (p == null) {
            System.out.println("Person: " + id + " is not in Redis");
            System.out.println("Searching Person: " + id + " in DB");
            person = personRepository.getOne(id);
            redisTemplate.opsForValue().set("user" + id, JSON.toJSONString(person));
        } else {
            person = JSON.parseObject(p, Person.class);
            System.out.println("Person: " + id + " is in Redis");
        }
        return person;
    }
}
