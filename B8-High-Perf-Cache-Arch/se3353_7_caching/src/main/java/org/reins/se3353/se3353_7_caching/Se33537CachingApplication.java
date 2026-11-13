package org.reins.se3353.se3353_7_caching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class Se33537CachingApplication {

    public static void main(String[] args) {
        SpringApplication.run(Se33537CachingApplication.class, args);
    }

}
