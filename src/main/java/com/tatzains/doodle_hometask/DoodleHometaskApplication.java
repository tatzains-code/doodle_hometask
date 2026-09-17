package com.tatzains.doodle_hometask;

import com.tatzains.doodle_hometask.config.SchedulingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SchedulingProperties.class)
public class DoodleHometaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(DoodleHometaskApplication.class, args);
    }

}
