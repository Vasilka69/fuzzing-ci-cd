package ru.diplom.cicd.master.security;

import java.security.Principal;
import java.util.UUID;

public record RequestUser(UUID id, String login) implements Principal {
    @Override
    public String getName() {
        return login == null ? "anonymous" : login;
    }
}
