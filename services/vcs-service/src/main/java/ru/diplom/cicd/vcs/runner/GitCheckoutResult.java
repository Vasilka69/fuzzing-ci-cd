package ru.diplom.cicd.vcs.runner;

public record GitCheckoutResult(String commitHash, String logs) {}
