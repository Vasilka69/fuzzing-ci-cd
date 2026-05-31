package ru.diplom.cicd.demo.mockmaster.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.diplom.cicd.contracts.job.JobMessage;
import ru.diplom.cicd.contracts.job.JobType;
import ru.diplom.cicd.demo.mockmaster.config.MockMasterPublisherProperties;

class DemoPipelineFactoryTest {

    private static final Instant NOW = Instant.parse("2026-05-31T00:00:00Z");

    private final DemoPipelineFactory factory = new DemoPipelineFactory(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsPredefinedPipelineForExecutorTopics() {
        MockMasterPublisherProperties properties = new MockMasterPublisherProperties("defense-demo", null, null);

        List<DemoJobPublication> publications = factory.create(properties);

        assertThat(publications).hasSize(5);
        assertThat(publications)
                .extracting(DemoJobPublication::topic)
                .containsExactly("jobs.vcs", "jobs.build", "jobs.fuzzing", "jobs.deploy", "jobs.script");
        assertThat(publications)
                .extracting(publication -> publication.message().jobType())
                .containsExactly(JobType.VCS, JobType.BUILD, JobType.FUZZING, JobType.DEPLOY, JobType.SCRIPT);
        assertThat(publications)
                .extracting(publication -> publication.message().templatePath())
                .containsExactly("vcs/git", "build/maven", "fuzzing/afl-llm", "deploy/file-copy", "script/bash");
    }

    @Test
    void createsContractCompatibleMessages() {
        MockMasterPublisherProperties properties = new MockMasterPublisherProperties("defense-demo", null, null);

        List<JobMessage> messages = factory.create(properties).stream()
                .map(DemoJobPublication::message)
                .toList();

        assertThat(messages)
            .isNotEmpty()
            .allSatisfy(message -> {
                assertThat(message.schemaVersion()).isEqualTo(1);
                assertThat(message.messageId()).isNotNull();
                assertThat(message.correlationId()).isNotNull();
                assertThat(message.pipelineRunId()).isNotNull();
                assertThat(message.pipelineId()).isNotNull();
                assertThat(message.stageId()).isNotNull();
                assertThat(message.jobId()).isNotNull();
                assertThat(message.jobExecutionId()).isNotNull();
                assertThat(message.attempt()).isEqualTo(1);
                assertThat(message.maxAttempts()).isEqualTo(1);
                assertThat(message.timeoutSeconds()).isPositive();
                assertThat(message.workspacePolicy().cleanup()).isEqualTo("always");
                assertThat(message.sandboxPolicy().runAsNonRoot()).isTrue();
                assertThat(message.sandboxPolicy().allowPrivilegeEscalation()).isFalse();
                assertThat(message.sandboxPolicy().networkPolicy()).isEqualTo("none");
                assertThat(message.secrets()).containsEntry("refs", List.of());
                assertThat(message.createdAt()).isEqualTo(NOW);
            });
    }

    @Test
    void usesJobExecutionIdAsKafkaKey() {
        MockMasterPublisherProperties properties = new MockMasterPublisherProperties("defense-demo", null, null);

        List<DemoJobPublication> publications = factory.create(properties);

        assertThat(publications)
            .isNotEmpty()
            .allSatisfy(publication -> assertThat(publication.key())
                .isEqualTo(publication.message().jobExecutionId().toString()));
    }

    @Test
    void referencesPreviousStageArtifactUris() {
        MockMasterPublisherProperties properties = new MockMasterPublisherProperties("defense-demo", null, null);

        List<JobMessage> messages = factory.create(properties).stream()
                .map(DemoJobPublication::message)
                .toList();

        UUID vcsExecutionId = messages.get(0).jobExecutionId();
        UUID buildExecutionId = messages.get(1).jobExecutionId();
        UUID fuzzingExecutionId = messages.get(2).jobExecutionId();

        String sourceSnapshotUri = "storage://source-snapshots/%s/source-snapshot.tar.gz".formatted(vcsExecutionId);
        String buildArtifactUri = "storage://build-artifacts/%s/build-artifacts.tar.gz".formatted(buildExecutionId);
        String fuzzingReportUri = "storage://fuzzing-reports/%s/fuzzing-report.tar.gz".formatted(fuzzingExecutionId);

        assertThat(messages.get(1).params()).containsEntry("source_snapshot_uri", sourceSnapshotUri);
        assertThat(messages.get(2).params()).containsEntry("target_artifact_uri", buildArtifactUri);
        assertThat(messages.get(3).params()).containsEntry("artifact_uri", buildArtifactUri);
        assertThat(messages.get(4).inputs()).containsEntry("fuzzingReportUri", fuzzingReportUri);
        assertThat(messages.get(4).params())
            .containsEntry("input_artifacts", List.of(
                Map.of("uri", buildArtifactUri, "path", "input/build-artifacts.tar.gz"),
                Map.of("uri", fuzzingReportUri, "path", "input/fuzzing-report.tar.gz")));
    }

    @Test
    void customTopicsOverrideDefaults() {
        MockMasterPublisherProperties properties = new MockMasterPublisherProperties(
                "defense-demo",
                null,
                new MockMasterPublisherProperties.Topics(
                        "demo.vcs", "demo.build", "demo.fuzzing", "demo.deploy", "demo.script"));

        List<DemoJobPublication> publications = factory.create(properties);

        assertThat(publications)
                .extracting(DemoJobPublication::topic)
                .containsExactly("demo.vcs", "demo.build", "demo.fuzzing", "demo.deploy", "demo.script");
    }
}
