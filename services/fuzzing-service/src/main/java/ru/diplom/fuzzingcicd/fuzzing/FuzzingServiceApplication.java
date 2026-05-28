package ru.diplom.fuzzingcicd.fuzzing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FuzzingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FuzzingServiceApplication.class, args);
    }
}
