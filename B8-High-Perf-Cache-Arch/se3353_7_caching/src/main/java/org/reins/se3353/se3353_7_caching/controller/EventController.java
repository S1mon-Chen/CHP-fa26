package org.reins.se3353.se3353_7_caching.controller;

import org.reins.se3353.se3353_7_caching.entity.Event;
import org.reins.se3353.se3353_7_caching.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventController {

    @Autowired
    private EventService eventService;

    @GetMapping(value = "/findEvent/{id}")
    public Event findEvent(@PathVariable("id") Integer id) {
        System.out.println("Searching Event: " + id);
        return eventService.findEventById(id);
    }
}
