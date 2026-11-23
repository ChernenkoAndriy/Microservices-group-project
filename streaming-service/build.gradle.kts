import com.google.protobuf.gradle.id

plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.25.0"
    id("com.google.protobuf") version "0.9.5"
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
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.springframework.boot:spring-boot-starter-grpc-client")
    implementation("io.minio:minio:8.5.10")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-micrometer-tracing-test")
    testRuntimeOnly("com.h2database:h2")
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

sourceSets.main {
    java.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/java"))
    java.srcDir(layout.buildDirectory.dir("generated/events/src/main/java"))
}

// gRPC client of catalog-service: Java classes generated from the shared contract
// api-contracts/catalog-service/grpc/*.proto into build/. protoc and the grpc-java plugin use the versions managed
// by the Spring Boot BOM, the same as the runtime jars.
val protobufVersion = dependencyManagement.importedProperties["protobuf-java.version"]
val grpcVersion = dependencyManagement.importedProperties["grpc-java.version"]

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:$protobufVersion" }
    plugins {
        id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion" }
    }
    generateProtoTasks {
        all().configureEach {
            plugins { maybeCreate("grpc").option("@generated=omit") }
        }
    }
}

sourceSets.main {
    proto { srcDir("$rootDir/../api-contracts/catalog-service/grpc") }
}

tasks.compileJava { dependsOn(tasks.openApiGenerate, openApiGenerateEvents) }
