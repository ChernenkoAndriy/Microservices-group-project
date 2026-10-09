plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.25.0"
}

group = "com.epam.java.specialization"
version = "0.0.1-SNAPSHOT"
description = "streaming-service"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.minio:minio:8.5.10")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// MapStruct mappers are Spring beans, and a target property left unmapped is a compile error.
tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf(
        "-Amapstruct.defaultComponentModel=spring",
        "-Amapstruct.unmappedTargetPolicy=ERROR",
    ))
}

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$rootDir/../api-contracts/streaming-service/openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
    apiPackage.set("com.epam.java.specialization.streamingservice.api")
    modelPackage.set("com.epam.java.specialization.streamingservice.api.dto")
    modelNameSuffix.set("Dto")
    typeMappings.set(mapOf("URI" to "String"))
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useSpringBoot4" to "true",
            "useJackson3" to "true",
            "useTags" to "true",
            "useBeanValidation" to "true",
            "useSpringBuiltInValidation" to "true",
            "openApiNullable" to "false",
            "skipDefaultInterface" to "false",
            "documentationProvider" to "none",
            "annotationLibrary" to "none",
            "generateJsonIncludeAnnotations" to "false",
            "generateJsonSetterNullsAnnotations" to "false",
        )
    )
}

// Kafka event payloads (api-contracts/streaming-service/asyncapi.yaml), generated as plain models.
val openApiGenerateEvents by tasks.registering(org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class) {
    generatorName.set("spring")
    inputSpec.set("$rootDir/../api-contracts/streaming-service/event-models.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/events").get().asFile.path)
    modelPackage.set("com.epam.java.specialization.streamingservice.event")
    globalProperties.set(mapOf("models" to "", "modelDocs" to "false", "modelTests" to "false"))
    configOptions.set(mapOf(
        "useSpringBoot4" to "true",
        "useJackson3" to "true",
        "useBeanValidation" to "true",
        "openApiNullable" to "false",
        "documentationProvider" to "none",
        "annotationLibrary" to "none",
        "generateJsonIncludeAnnotations" to "false",
        "generateJsonSetterNullsAnnotations" to "false",
    ))
}

// Client for catalog-service's internal API (api-contracts/catalog-service/internal-openapi.yaml).
val openApiGenerateCatalogClient by tasks.registering(org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class) {
    generatorName.set("spring")
    library.set("spring-http-interface")
    inputSpec.set("$rootDir/../api-contracts/catalog-service/internal-openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/catalog-client").get().asFile.path)
    apiPackage.set("com.epam.java.specialization.streamingservice.client.catalog.api")
    modelPackage.set("com.epam.java.specialization.streamingservice.client.catalog.dto")
    modelNameSuffix.set("Dto")
    typeMappings.set(mapOf("URI" to "String"))
    configOptions.set(mapOf(
        "useSpringBoot4" to "true",
        "useJackson3" to "true",
        "useTags" to "true",
        "openApiNullable" to "false",
        "documentationProvider" to "none",
        "annotationLibrary" to "none",
        "generateJsonIncludeAnnotations" to "false",
        "generateJsonSetterNullsAnnotations" to "false",
        "configPackage" to "org.openapitools.configuration.catalogclient",
    ))
}

sourceSets.main {
    java.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/java"))
    java.srcDir(layout.buildDirectory.dir("generated/events/src/main/java"))
    java.srcDir(layout.buildDirectory.dir("generated/catalog-client/src/main/java"))
}
tasks.compileJava { dependsOn(tasks.openApiGenerate, openApiGenerateEvents, openApiGenerateCatalogClient) }
