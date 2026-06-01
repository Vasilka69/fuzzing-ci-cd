import { useAuth } from "@/app/AuthProvider";
import { pipelinesApi } from "@/api/pipelinesApi";
import { pipelineStructureApi } from "@/api/pipelineStructureApi";
import { AccessDeniedCard } from "@/shared/components/AccessDeniedCard";
import { ApiErrorAlert } from "@/shared/components/ApiErrorAlert";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Card, Checkbox, Col, Form, Input, InputNumber, Row, Select, Space, Table, Tabs, message } from "antd";
import { useMemo } from "react";
import { useParams } from "react-router-dom";

type StageForm = {
  position: number;
  name: string;
  description?: string;
  runPolicy?: string;
};

type JobForm = {
  stageId: string;
  position: number;
  name: string;
  jobType: string;
  condition?: string;
  timeoutSeconds?: number;
  maxAttempts?: number;
  continueOnError?: boolean;
  params?: string;
};

type DependencyForm = {
  jobId: string;
  dependsOnJobId: string;
  condition?: string;
};

export function PipelineDesignerPage() {
  const { hasCapability } = useAuth();
  const { id } = useParams();
  const pipelineId = id ?? "";
  const queryClient = useQueryClient();
  const [messageApi, contextHolder] = message.useMessage();

  const [stageForm] = Form.useForm<StageForm>();
  const [jobForm] = Form.useForm<JobForm>();
  const [dependencyForm] = Form.useForm<DependencyForm>();
  const canView = hasCapability("view");
  const canEdit = hasCapability("edit");

  const detailsQuery = useQuery({
    queryKey: ["pipeline-details", pipelineId],
    queryFn: () => pipelinesApi.details(pipelineId),
    enabled: Boolean(pipelineId) && canView
  });

  const createStageMutation = useMutation({
    mutationFn: (payload: StageForm) => pipelineStructureApi.createStage(pipelineId, payload),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["pipeline-details", pipelineId] });
      messageApi.success("Stage created");
      stageForm.resetFields();
    }
  });

  const createJobMutation = useMutation({
    mutationFn: async (values: JobForm) => {
      let params: Record<string, unknown> | undefined;
      if (values.params?.trim()) {
        try {
          params = JSON.parse(values.params) as Record<string, unknown>;
        } catch {
          messageApi.error("Job params must be valid JSON");
          throw new Error("invalid_json");
        }
      }
      return pipelineStructureApi.createJob(values.stageId, {
        position: values.position,
        name: values.name,
        jobType: values.jobType,
        condition: values.condition || undefined,
        timeoutSeconds: values.timeoutSeconds,
        maxAttempts: values.maxAttempts,
        continueOnError: values.continueOnError,
        params
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["pipeline-details", pipelineId] });
      messageApi.success("Job created");
      jobForm.resetFields();
    }
  });

  const createDependencyMutation = useMutation({
    mutationFn: (payload: DependencyForm) =>
      pipelineStructureApi.addDependency(payload.jobId, {
        dependsOnJobId: payload.dependsOnJobId,
        condition: payload.condition
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["pipeline-details", pipelineId] });
      messageApi.success("Dependency added");
      dependencyForm.resetFields();
    }
  });

  const jobsByStage = useMemo(() => {
    const stageMap = new Map<string, string>();
    (detailsQuery.data?.stages ?? []).forEach((stage) => stageMap.set(stage.id, stage.name));
    return (detailsQuery.data?.jobs ?? []).map((job) => ({
      ...job,
      stageName: stageMap.get(job.stageId) ?? job.stageId.slice(0, 8)
    }));
  }, [detailsQuery.data?.stages, detailsQuery.data?.jobs]);

  if (!canView) {
    return <AccessDeniedCard subtitle="Viewing pipeline design requires `view` capability." />;
  }

  if (!canEdit) {
    return <AccessDeniedCard title="Designer Locked" subtitle="Editing pipeline structure requires `edit` capability." />;
  }

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      {contextHolder}
      <div className="glass-card" style={{ padding: 20 }}>
        <h2 className="section-title">Pipeline Designer</h2>
        <p className="section-subtitle">Create stages, jobs, and dependencies for pipeline {pipelineId.slice(0, 8)}.</p>
      </div>

      <ApiErrorAlert
        error={
          detailsQuery.error || createStageMutation.error || createJobMutation.error || createDependencyMutation.error
        }
      />

      <Row gutter={16}>
        <Col xs={24} xl={12}>
          <Card className="glass-card" title="Current Stages">
            <Table
              rowKey="id"
              size="small"
              loading={detailsQuery.isLoading}
              dataSource={detailsQuery.data?.stages ?? []}
              pagination={false}
              columns={[
                { title: "Position", dataIndex: "position", width: 90 },
                { title: "Name", dataIndex: "name" },
                { title: "Policy", dataIndex: "runPolicy", width: 140 }
              ]}
            />
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card className="glass-card" title="Current Jobs">
            <Table
              rowKey="id"
              size="small"
              loading={detailsQuery.isLoading}
              dataSource={jobsByStage}
              pagination={{ pageSize: 7 }}
              columns={[
                { title: "Name", dataIndex: "name" },
                { title: "Type", dataIndex: "jobType", width: 120 },
                { title: "Stage", dataIndex: "stageName", width: 140 },
                { title: "Pos", dataIndex: "position", width: 80 }
              ]}
            />
          </Card>
        </Col>
      </Row>

      <Tabs
        items={[
          {
            key: "stage",
            label: "Create Stage",
            children: (
              <Card className="glass-card">
                <Form
                  layout="vertical"
                  form={stageForm}
                  initialValues={{ position: 1, runPolicy: "sequential" }}
                  onFinish={(values) => createStageMutation.mutate(values)}
                >
                  <Row gutter={16}>
                    <Col xs={24} md={8}>
                      <Form.Item name="position" label="Position" rules={[{ required: true }]}>
                        <InputNumber min={1} style={{ width: "100%" }} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={16}>
                      <Form.Item name="name" label="Name" rules={[{ required: true }]}>
                        <Input />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item name="runPolicy" label="Run Policy">
                        <Select options={[{ value: "sequential" }, { value: "parallel" }]} />
                      </Form.Item>
                    </Col>
                    <Col xs={24}>
                      <Form.Item name="description" label="Description">
                        <Input.TextArea rows={2} />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Button type="primary" htmlType="submit" loading={createStageMutation.isPending}>
                    Add Stage
                  </Button>
                </Form>
              </Card>
            )
          },
          {
            key: "job",
            label: "Create Job",
            children: (
              <Card className="glass-card">
                <Form
                  layout="vertical"
                  form={jobForm}
                  initialValues={{ position: 1, jobType: "script", condition: "on_success", maxAttempts: 1, timeoutSeconds: 600 }}
                  onFinish={(values) => createJobMutation.mutate(values)}
                >
                  <Row gutter={16}>
                    <Col xs={24} md={8}>
                      <Form.Item name="stageId" label="Stage" rules={[{ required: true }]}>
                        <Select options={(detailsQuery.data?.stages ?? []).map((stage) => ({ value: stage.id, label: stage.name }))} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="position" label="Position" rules={[{ required: true }]}>
                        <InputNumber min={1} style={{ width: "100%" }} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={8}>
                      <Form.Item name="jobType" label="Job Type" rules={[{ required: true }]}>
                        <Select
                          options={["vcs", "storage", "build", "fuzzing", "deploy", "script"].map((v) => ({
                            value: v,
                            label: v
                          }))}
                        />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item name="name" label="Name" rules={[{ required: true }]}>
                        <Input />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={6}>
                      <Form.Item name="condition" label="Condition">
                        <Select options={["on_success", "on_failure", "always"].map((v) => ({ value: v, label: v }))} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={3}>
                      <Form.Item name="maxAttempts" label="Attempts">
                        <InputNumber min={1} style={{ width: "100%" }} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={3}>
                      <Form.Item name="timeoutSeconds" label="Timeout">
                        <InputNumber min={10} style={{ width: "100%" }} />
                      </Form.Item>
                    </Col>
                    <Col xs={24}>
                      <Form.Item name="params" label="Params JSON">
                        <Input.TextArea rows={3} placeholder='{"branch":"main"}' />
                      </Form.Item>
                    </Col>
                    <Col xs={24}>
                      <Form.Item name="continueOnError" valuePropName="checked">
                        <Checkbox>Continue on error</Checkbox>
                      </Form.Item>
                    </Col>
                  </Row>
                  <Button type="primary" htmlType="submit" loading={createJobMutation.isPending}>
                    Add Job
                  </Button>
                </Form>
              </Card>
            )
          },
          {
            key: "dependency",
            label: "Add Dependency",
            children: (
              <Card className="glass-card">
                <Form layout="vertical" form={dependencyForm} onFinish={(values) => createDependencyMutation.mutate(values)}>
                  <Row gutter={16}>
                    <Col xs={24} md={10}>
                      <Form.Item name="jobId" label="Job" rules={[{ required: true }]}>
                        <Select options={(detailsQuery.data?.jobs ?? []).map((job) => ({ value: job.id, label: `${job.name} (${job.id.slice(0, 6)})` }))} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={10}>
                      <Form.Item name="dependsOnJobId" label="Depends On" rules={[{ required: true }]}>
                        <Select options={(detailsQuery.data?.jobs ?? []).map((job) => ({ value: job.id, label: `${job.name} (${job.id.slice(0, 6)})` }))} />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={4}>
                      <Form.Item name="condition" label="Condition">
                        <Select options={["on_success", "on_failure", "always"].map((v) => ({ value: v, label: v }))} />
                      </Form.Item>
                    </Col>
                  </Row>
                  <Button type="primary" htmlType="submit" loading={createDependencyMutation.isPending}>
                    Add Dependency
                  </Button>
                </Form>
              </Card>
            )
          }
        ]}
      />
    </Space>
  );
}
