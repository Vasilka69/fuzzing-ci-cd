package ru.diplom.cicd.master.domain.enums;

public enum Permission {
    VIEW("view"),
    EDIT("edit"),
    RUN("run"),
    CANCEL("cancel"),
    APPROVE_DEPLOYMENT("approve_deployment"),
    MANAGE_SECRETS("manage_secrets"),
    MANAGE_CONNECTIONS("manage_connections"),
    ADMIN("admin");

    private final String value;

    Permission(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
