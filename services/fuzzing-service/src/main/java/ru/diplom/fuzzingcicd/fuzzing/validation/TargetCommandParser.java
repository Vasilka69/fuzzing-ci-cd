package ru.diplom.fuzzingcicd.fuzzing.validation;

import java.util.ArrayList;
import java.util.List;
import ru.diplom.fuzzingcicd.fuzzing.domain.TargetCommand;

public final class TargetCommandParser {

    private TargetCommandParser() {
    }

    public static TargetCommand parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("target_command must not be blank");
        }
        rejectShellSyntax(value);

        List<String> argv = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean escaping = false;

        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (escaping) {
                token.append(current);
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            if (quote != 0) {
                if (current == quote) {
                    quote = 0;
                } else {
                    token.append(current);
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            if (Character.isWhitespace(current)) {
                appendToken(argv, token);
                continue;
            }
            token.append(current);
        }

        if (escaping) {
            throw new IllegalArgumentException("target_command ends with an incomplete escape");
        }
        if (quote != 0) {
            throw new IllegalArgumentException("target_command has an unterminated quote");
        }
        appendToken(argv, token);
        if (argv.isEmpty()) {
            throw new IllegalArgumentException("target_command must contain an executable");
        }
        return new TargetCommand(argv, argv.stream().anyMatch(arg -> arg.contains("@@")));
    }

    private static void appendToken(List<String> argv, StringBuilder token) {
        if (!token.isEmpty()) {
            argv.add(token.toString());
            token.setLength(0);
        }
    }

    private static void rejectShellSyntax(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\n' || current == '\r' || current == '\t') {
                throw new IllegalArgumentException("target_command must be a single command line");
            }
            if (";&|<>`$".indexOf(current) >= 0) {
                throw new IllegalArgumentException("target_command contains shell syntax");
            }
        }
    }
}
