package ru.diplom.cicd.master.service.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DeadLetterService {

    public void record(String reason, Object payload) {
        log.warn("Dead letter event: reason={}, payload={}", reason, payload);
    }
}
