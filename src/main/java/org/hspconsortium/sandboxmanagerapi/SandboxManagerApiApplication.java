package org.hspconsortium.sandboxmanagerapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@ComponentScan({"org.hspconsortium"})
@SpringBootApplication
@EnableScheduling
public class SandboxManagerApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SandboxManagerApiApplication.class, args);
    }

}
