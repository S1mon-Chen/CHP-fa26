package org.reins.se3353.se3353_7_springredissample.serviceimpl;

import org.reins.se3353.se3353_7_springredissample.dao.EventDao;
import org.reins.se3353.se3353_7_springredissample.entity.Event;
import org.reins.se3353.se3353_7_springredissample.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Created by chenhaopeng on 2019/5/2.
 */
@Service
public class EventServiceImpl implements EventService {

    @Autowired
    private EventDao eventDao;

    @Override
    public Event findEventById(Integer id){
        return eventDao.findOne(id);
    }
}
