package ru.diplom.cicd.master.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.diplom.cicd.master.domain.entity.JobExecutionEntity;
import ru.diplom.cicd.master.domain.entity.PipelineRunEntity;
import ru.diplom.cicd.master.exception.ApiException;
import ru.diplom.cicd.master.repository.CancellationRequestRepository;
import ru.diplom.cicd.master.repository.DeploymentApprovalRepository;
import ru.diplom.cicd.master.repository.DeploymentEnvironmentRepository;
import ru.diplom.cicd.master.repository.JobExecutionRepository;
import ru.diplom.cicd.master.repository.JobRepository;
import ru.diplom.cicd.master.repository.JobTemplateRepository;
import ru.diplom.cicd.master.repository.PipelineRepository;
import ru.diplom.cicd.master.repository.PipelineRunRepository;
import ru.diplom.cicd.master.service.AuditService;
import ru.diplom.cicd.master.service.PermissionService;
import ru.diplom.cicd.master.service.PipelineRunService;
import ru.diplom.cicd.master.service.UserContextService;
import ru.diplom.cicd.master.service.messaging.JobMessageFactory;
import ru.diplom.cicd.master.service.messaging.KafkaTopicResolver;
import ru.diplom.cicd.master.service.messaging.outbox.OutboxService;
import ru.diplom.cicd.master.service.orchestration.PipelineOrchestrator;
import ru.diplom.cicd.master.service.orchestration.RetryPolicyService;
import ru.diplom.cicd.master.service.security.SensitiveDataSanitizer;

class PipelineRunEventsCursorTest {

    private PipelineRunRepository pipelineRunRepository;
    private JobExecutionRepository jobExecutionRepository;
    private UserContextService userContextService;
    private PipelineRunService service;

    @BeforeEach
    void setUp() {
        pipelineRunRepository = mock(PipelineRunRepository.class);
        jobExecutionRepository = mock(JobExecutionRepository.class);
        userContextService = mock(UserContextService.class);
        PermissionService permissionService = mock(PermissionService.class);

        service = new PipelineRunService(
                mock(PipelineRepository.class),
                pipelineRunRepository,
                mock(JobRepository.class),
                jobExecutionRepository,
                mock(JobTemplateRepository.class),
                mock(DeploymentEnvironmentRepository.class),
                mock(DeploymentApprovalRepository.class),
                mock(CancellationRequestRepository.class),
                mock(PipelineOrchestrator.class),
                mock(JobMessageFactory.class),
                mock(KafkaTopicResolver.class),
                mock(OutboxService.class),
                permissionService,
                userContextService,
                mock(AuditService.class),
                mock(RetryPolicyService.class),
                new ObjectMapper(),
                mock(SensitiveDataSanitizer.class)
        );
    }

    @Test
    void returnsCursorPageWithStableOrdering() {
        UUID runId = UUID.randomUUID();
        PipelineRunEntity run = PipelineRunEntity.builder()
                .id(runId)
                .pipelineId(UUID.randomUUID())
                .status("running")
                .correlationId(UUID.randomUUID())
                .startedAt(OffsetDateTime.now())
                .triggerPayload(new ObjectMapper().createObjectNode())
                .triggeredByType("user")
                .build();

        JobExecutionEntity newest = execution(runId, OffsetDateTime.of(2026, 5, 31, 12, 0, 0, 0, ZoneOffset.UTC));
        JobExecutionEntity middle = execution(runId, OffsetDateTime.of(2026, 5, 31, 11, 0, 0, 0, ZoneOffset.UTC));
        JobExecutionEntity oldest = execution(runId, OffsetDateTime.of(2026, 5, 31, 10, 0, 0, 0, ZoneOffset.UTC));

        when(userContextService.currentUserIdOrNull()).thenReturn(UUID.randomUUID());
        when(pipelineRunRepository.findById(runId)).thenReturn(java.util.Optional.of(run));
        when(jobExecutionRepository.findByPipelineRunId(runId)).thenReturn(List.of(oldest, middle, newest));

        var firstPage = service.listRunEvents(runId, null, 2);
        assertEquals(2, firstPage.items().size());
        assertEquals(newest.getId(), firstPage.items().get(0).getId());
        assertEquals(middle.getId(), firstPage.items().get(1).getId());
        assertNotNull(firstPage.nextCursor());

        var secondPage = service.listRunEvents(runId, firstPage.nextCursor(), 2);
        assertEquals(1, secondPage.items().size());
        assertEquals(oldest.getId(), secondPage.items().getFirst().getId());
    }

    @Test
    void rejectsInvalidCursorFormat() {
        UUID runId = UUID.randomUUID();
        PipelineRunEntity run = PipelineRunEntity.builder()
                .id(runId)
                .pipelineId(UUID.randomUUID())
                .status("running")
                .correlationId(UUID.randomUUID())
                .startedAt(OffsetDateTime.now())
                .triggerPayload(new ObjectMapper().createObjectNode())
                .triggeredByType("user")
                .build();

        when(userContextService.currentUserIdOrNull()).thenReturn(UUID.randomUUID());
        when(pipelineRunRepository.findById(runId)).thenReturn(java.util.Optional.of(run));
        when(jobExecutionRepository.findByPipelineRunId(runId)).thenReturn(List.of());

        ApiException ex = assertThrows(ApiException.class, () -> service.listRunEvents(runId, "bad-cursor", 10));
        assertEquals("invalid_cursor", ex.getCode());
    }

    private JobExecutionEntity execution(UUID runId, OffsetDateTime updatedAt) {
        return JobExecutionEntity.builder()
                .id(UUID.randomUUID())
                .pipelineRunId(runId)
                .jobId(UUID.randomUUID())
                .attempt(1)
                .status("success")
                .result(new ObjectMapper().createObjectNode())
                .metrics(new ObjectMapper().createObjectNode())
                .artifactManifest(new ObjectMapper().createArrayNode())
                .createdAt(updatedAt.minusMinutes(1))
                .updatedAt(updatedAt)
                .build();
    }
}
