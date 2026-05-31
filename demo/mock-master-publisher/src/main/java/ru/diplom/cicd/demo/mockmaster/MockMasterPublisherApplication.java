package ru.diplom.cicd.demo.mockmaster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import ru.diplom.cicd.demo.mockmaster.config.ApplicationConfig;

@SpringBootApplication
@Import(ApplicationConfig.class)
public class MockMasterPublisherApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockMasterPublisherApplication.class, args);
    }
}
