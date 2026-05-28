package ru.diplom.fuzzingcicd.fuzzing.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ru.diplom.fuzzingcicd.fuzzing.domain.TargetCommand;

class TargetCommandParserTest {

    @Test
    void parsesQuotedArgumentsWithoutInvokingShell() {
        TargetCommand command = TargetCommandParser.parse("./target --mode 'fast path' @@");

        assertThat(command.argv()).containsExactly("./target", "--mode", "fast path", "@@");
        assertThat(command.usesFilePlaceholder()).isTrue();
    }

    @Test
    void rejectsShellOperators() {
        assertThatThrownBy(() -> TargetCommandParser.parse("./target @@ | tee out"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shell syntax");
    }

    @Test
    void rejectsUnterminatedQuote() {
        assertThatThrownBy(() -> TargetCommandParser.parse("./target 'broken"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unterminated quote");
    }
}
