package ru.diplom.cicd.deploy.runner;

public record SshBashTarget(String host, int port, String user, String credentialsRef) {}
