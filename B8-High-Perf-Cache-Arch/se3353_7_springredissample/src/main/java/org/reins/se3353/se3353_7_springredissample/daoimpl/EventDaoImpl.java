package org.reins.se3353.se3353_7_springredissample.daoimpl;

import org.reins.se3353.se3353_7_springredissample.dao.EventDao;
import org.reins.se3353.se3353_7_springredissample.entity.Event;
import org.reins.se3353.se3353_7_springredissample.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * Created by chenhaopeng on 2019/5/2.
 */
@Repository
public class EventDaoImpl implements EventDao {
        @Autowired
        private EventRepository eventRepository;


        @Override
        public Event findOne(Integer id) {
            return eventRepository.getOne(id);
        }
}
