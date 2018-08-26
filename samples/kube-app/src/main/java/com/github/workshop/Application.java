package com.github.workshop;

import com.github.workshop.config.AppConfig;
import com.github.workshop.service.EchoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class Application {

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private EchoService echoService;

    @GetMapping("")
    public ResponseEntity hello() {
        return ResponseEntity.ok(appConfig.getMessage());
    }

    @GetMapping("/echo/{resource}")
    public ResponseEntity echo(@PathVariable("resource") String resource) {
        return ResponseEntity.ok(echoService.echo(resource));
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
