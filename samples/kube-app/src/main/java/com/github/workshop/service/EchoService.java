package com.github.workshop.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

@Service
public class EchoService {

    @Autowired
    private DiscoveryClient discoveryClient;

    public String echo(String resource) {

        List<ServiceInstance> instances = discoveryClient.getInstances("echo");
        Optional<ServiceInstance> instance = instances.stream().findAny();

        if (!instance.isPresent()) {
            throw new RuntimeException("Echo service not found now");
        }

        ResponseEntity<String> response = new RestTemplate()
                .getForEntity(String.format("%s/echo/%s", instance.get().getUri(), resource), String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            return response.getBody();
        }

        throw new RuntimeException("Echo service unreachable now");
    }

}
