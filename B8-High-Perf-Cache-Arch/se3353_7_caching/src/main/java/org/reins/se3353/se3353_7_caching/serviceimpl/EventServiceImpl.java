package org.reins.se3353.se3353_7_caching.serviceimpl;

import org.reins.se3353.se3353_7_caching.dao.EventDao;
import org.reins.se3353.se3353_7_caching.entity.Event;
import org.reins.se3353.se3353_7_caching.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EventServiceImpl implements EventService {

    @Autowired
    private EventDao eventDao;

    @Override
    public Event findEventById(Integer id){
        return eventDao.findOne(id);
    }
}
