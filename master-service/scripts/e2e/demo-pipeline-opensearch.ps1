param(
    [string]$ApiBaseUrl = "http://localhost:8080/api/v1",
    [string]$OpenSearchUrl = "http://localhost:9200",
    [string]$OpenSearchIndex = "cicd-executor-events",
    [string]$Login = "e2e-demo",
    [string]$Password = "e2e-demo",
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$message) {
    Write-Host "[e2e] $message"
}

function Invoke-MasterApi {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [hashtable]$Body = $null
    )

    $uri = "$ApiBaseUrl$Path"
    if ($null -eq $Body) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $script:AuthHeaders
    }

    $json = $Body | ConvertTo-Json -Depth 20
    return Invoke-RestMethod -Method $Method -Uri $uri -Headers $script:AuthHeaders -Body $json -ContentType "application/json"
}

function Wait-Until {
    param(
        [Parameter(Mandatory = $true)][scriptblock]$Condition,
        [Parameter(Mandatory = $true)][string]$FailureMessage,
        [int]$Timeout = 60,
        [int]$DelaySeconds = 2
    )

    $deadline = (Get-Date).AddSeconds($Timeout)
    while ((Get-Date) -lt $deadline) {
        $value = & $Condition
        if ($value) {
            return $value
        }
        Start-Sleep -Seconds $DelaySeconds
    }
    throw $FailureMessage
}

function Publish-ExecutorEvent {
    param(
        [Parameter(Mandatory = $true)]$Run,
        [Parameter(Mandatory = $true)]$Execution,
        [Parameter(Mandatory = $true)]$Job,
        [Parameter(Mandatory = $true)][string]$Status,
        [Parameter(Mandatory = $true)][string]$EventType
    )

    $now = (Get-Date).ToUniversalTime().ToString("o")
    $docId = "$($Execution.id)-$([guid]::NewGuid().ToString())"
    $body = @{
        schemaVersion = 1
        messageId = [guid]::NewGuid().ToString()
        correlationId = $Run.correlationId
        pipelineRunId = $Run.id
        pipelineId = $Run.pipelineId
        stageId = $Job.stageId
        jobId = $Execution.jobId
        jobExecutionId = $Execution.id
        jobType = $Job.jobType
        templatePath = "$($Job.jobType)/demo"
        eventType = $EventType
        status = $Status
        attempt = $Execution.attempt
        workerId = "e2e-worker"
        startedAt = $now
        finishedAt = if ($Status -eq "SUCCESS") { $now } else { $null }
        durationMs = if ($Status -eq "SUCCESS") { 500 } else { $null }
        summary = "e2e demo event $Status"
        artifacts = @()
        metrics = @{}
        additionalData = @{}
        ingestedAt = $now
        documentId = $docId
    }

    $json = $body | ConvertTo-Json -Depth 20
    $uri = "$OpenSearchUrl/$OpenSearchIndex/_doc/$docId?refresh=true"
    Invoke-RestMethod -Method Put -Uri $uri -Body $json -ContentType "application/json" | Out-Null
}

Write-Step "Login as '$Login'"
$loginResponse = Invoke-RestMethod -Method Post -Uri "$ApiBaseUrl/auth/login" -Body (@{
            login    = $Login
            password = $Password
        } | ConvertTo-Json) -ContentType "application/json"

$script:AuthHeaders = @{
    Authorization = "Bearer $($loginResponse.token)"
    "X-User-Id"   = "$($loginResponse.userId)"
    "X-User-Login" = "$($loginResponse.login)"
}

$pipelineName = "e2e-opensearch-" + ([guid]::NewGuid().ToString("N").Substring(0, 8))
Write-Step "Create pipeline $pipelineName"
$pipeline = Invoke-MasterApi -Method Post -Path "/pipelines" -Body @{
    name = $pipelineName
    description = "E2E pipeline with OpenSearch-driven executor events"
}

Write-Step "Create stages"
$stageBuild = Invoke-MasterApi -Method Post -Path "/pipelines/$($pipeline.id)/stages" -Body @{
    position = 1
    name = "build"
    runPolicy = "sequential"
}
$stageTest = Invoke-MasterApi -Method Post -Path "/pipelines/$($pipeline.id)/stages" -Body @{
    position = 2
    name = "test"
    runPolicy = "sequential"
}

Write-Step "Create jobs"
$jobBuild = Invoke-MasterApi -Method Post -Path "/stages/$($stageBuild.id)/jobs" -Body @{
    position = 1
    name = "build-script"
    jobType = "script"
    params = @{}
    condition = "on_success"
    timeoutSeconds = 120
    maxAttempts = 1
}
$jobTest = Invoke-MasterApi -Method Post -Path "/stages/$($stageTest.id)/jobs" -Body @{
    position = 1
    name = "test-script"
    jobType = "script"
    params = @{}
    condition = "on_success"
    timeoutSeconds = 120
    maxAttempts = 1
}

Write-Step "Run pipeline"
$run = Invoke-MasterApi -Method Post -Path "/pipelines/$($pipeline.id)/runs" -Body @{
    triggerType = "user"
    triggerPayload = @{
        source = "e2e-demo"
    }
}

Write-Step "Wait for first execution"
$firstExecution = Wait-Until -Timeout $TimeoutSeconds -FailureMessage "First execution was not created in time." -Condition {
    $graph = Invoke-MasterApi -Method Get -Path "/pipeline-runs/$($run.id)/graph"
    return $graph | Where-Object { $_.jobId -eq $jobBuild.id } | Select-Object -First 1
}

Write-Step "Publish RUNNING/SUCCESS for first execution via OpenSearch"
Publish-ExecutorEvent -Run $run -Execution $firstExecution -Job $jobBuild -Status "RUNNING" -EventType "JOB_STARTED"
Publish-ExecutorEvent -Run $run -Execution $firstExecution -Job $jobBuild -Status "SUCCESS" -EventType "JOB_FINISHED"

Write-Step "Wait for second execution"
$secondExecution = Wait-Until -Timeout $TimeoutSeconds -FailureMessage "Second execution was not created in time." -Condition {
    $graph = Invoke-MasterApi -Method Get -Path "/pipeline-runs/$($run.id)/graph"
    return $graph | Where-Object { $_.jobId -eq $jobTest.id } | Select-Object -First 1
}

Write-Step "Publish RUNNING/SUCCESS for second execution via OpenSearch"
Publish-ExecutorEvent -Run $run -Execution $secondExecution -Job $jobTest -Status "RUNNING" -EventType "JOB_STARTED"
Publish-ExecutorEvent -Run $run -Execution $secondExecution -Job $jobTest -Status "SUCCESS" -EventType "JOB_FINISHED"

Write-Step "Wait until run reaches success"
$finalRun = Wait-Until -Timeout $TimeoutSeconds -FailureMessage "Run did not reach success in time. Ensure EXECUTOR_EVENTS_TRANSPORT=opensearch in master-service." -Condition {
    $current = Invoke-MasterApi -Method Get -Path "/pipeline-runs/$($run.id)"
    if ($current.status -eq "success") {
        return $current
    }
    return $null
}

Write-Step "Done"
Write-Host "Pipeline ID: $($pipeline.id)"
Write-Host "Run ID: $($run.id)"
Write-Host "Final status: $($finalRun.status)"
