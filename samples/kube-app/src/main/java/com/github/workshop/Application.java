package com.github.workshop;

import com.github.workshop.config.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class Application {

    @Autowired
    private AppConfig appConfig;

    @GetMapping("")
    public ResponseEntity hello() {
        return ResponseEntity.ok(appConfig.getMessage());
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
