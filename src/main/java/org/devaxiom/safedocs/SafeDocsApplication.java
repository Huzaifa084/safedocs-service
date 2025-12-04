package org.devaxiom.safedocs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SafeDocsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SafeDocsApplication.class, args);
    }

}
