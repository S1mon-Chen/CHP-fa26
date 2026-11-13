package org.reins.se3353.se3353_7_caching.service;

import org.reins.se3353.se3353_7_caching.entity.Event;

public interface EventService {
    Event findEventById(Integer id);
}
