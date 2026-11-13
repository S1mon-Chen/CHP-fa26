package org.reins.se3353.se3353_7_caching.dao;

import org.reins.se3353.se3353_7_caching.entity.Event;

public interface EventDao {
    Event findOne(Integer id);
}
