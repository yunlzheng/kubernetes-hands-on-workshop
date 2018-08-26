package com.github.workshop.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class EchoService {

    public String echo(String resource) {
        ResponseEntity<String> response = new RestTemplate().getForEntity("http://127.0.0.1:8080/echo/" + resource, String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        }
        throw new RuntimeException("Echo service unreachable now");
    }

}
