package com.classhack;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class ClassHackApplication {

    @PostConstruct
    public void init() {
        // Enforce consistent UTC timezone for server persistence across all platforms (Render, local, docker)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ClassHackApplication.class, args);
    }
}
