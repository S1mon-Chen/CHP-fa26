package org.reins.se3353.se3353_7_caching.daoimpl;

import org.reins.se3353.se3353_7_caching.dao.EventDao;
import org.reins.se3353.se3353_7_caching.entity.Event;
import org.reins.se3353.se3353_7_caching.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class EventDaoImpl implements EventDao {
        @Autowired
        private EventRepository eventRepository;


        @Override
        public Event findOne(Integer id) {
            return eventRepository.getOne(id);
        }
}
