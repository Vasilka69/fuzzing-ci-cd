package ru.diplom.cicd.master.service;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import ru.diplom.cicd.master.security.RequestUser;

@Service
public class UserContextService {

    public RequestUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return new RequestUser(null, "anonymous");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof RequestUser requestUser) {
            return requestUser;
        }
        return new RequestUser(null, "anonymous");
    }

    public UUID currentUserIdOrNull() {
        return current().id();
    }
}
