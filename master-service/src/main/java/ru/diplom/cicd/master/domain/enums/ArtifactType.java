package ru.diplom.cicd.master.domain.enums;

public enum ArtifactType {
    SOURCE_SNAPSHOT("source_snapshot"),
    BUILD_ARTIFACT("build_artifact"),
    FUZZING_REPORT("fuzzing_report"),
    CRASH_CASE("crash_case"),
    HANG_CASE("hang_case"),
    CORPUS("corpus"),
    LOG("log"),
    DEPLOYMENT_MANIFEST("deployment_manifest"),
    SCRIPT_OUTPUT("script_output"),
    RELEASE_PACKAGE("release_package"),
    OTHER("other");

    private final String value;

    ArtifactType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
