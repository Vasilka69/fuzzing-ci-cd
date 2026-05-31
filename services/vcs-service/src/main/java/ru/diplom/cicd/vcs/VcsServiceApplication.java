package ru.diplom.cicd.vcs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.diplom.cicd.vcs.config.ApplicationConfig;

@SpringBootApplication
@ComponentScan(excludeFilters = @ComponentScan.Filter(Configuration.class))
@Import(ApplicationConfig.class)
public class VcsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VcsServiceApplication.class, args);
    }
}
