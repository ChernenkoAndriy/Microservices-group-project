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
description = "track-service"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val resilience4jVersion = "2.4.0"

dependencies {
    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-grpc-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("io.github.resilience4j:resilience4j-spring-boot4:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-retry:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-bulkhead:$resilience4jVersion")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-grpc-server-test")
    testImplementation("org.springframework.boot:spring-boot-micrometer-tracing-test")
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
    // Consumer-driven contract tests (Pact): the consumer tests write pacts/catalog-service-auth-service.json
    testImplementation("au.com.dius.pact.consumer:junit5:4.7.7")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")
    testCompileOnly("org.projectlombok:lombok:1.18.48")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.48")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Pact files go to <repo>/pacts, where the provider (auth-service) verification reads them from.
    systemProperty("pact.rootDir", "$rootDir/../pacts")
    systemProperty("pact.writer.overwrite", "true")
}

// ./gradlew contractTest: only the consumer contract tests (writes the pact file).
tasks.register<Test>("contractTest") {
    description = "Runs the Pact consumer tests and writes the pact file to ../pacts"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("contract") }
}

// MapStruct mappers are Spring beans, and a target property left unmapped is a compile error.
tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf(
        "-Amapstruct.defaultComponentModel=spring",
        "-Amapstruct.unmappedTargetPolicy=ERROR",
    ))
}

val springOptions = mapOf(
    "useSpringBoot4" to "true",
    "useJackson3" to "true",
    "useTags" to "true",
    "useBeanValidation" to "true",
    "useSpringBuiltInValidation" to "true",
    "openApiNullable" to "false",
    "documentationProvider" to "none",
    "annotationLibrary" to "none",
    "generateJsonIncludeAnnotations" to "false",
    "generateJsonSetterNullsAnnotations" to "false",
)
val stringUris = mapOf("URI" to "String")

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$rootDir/../api-contracts/catalog-service/openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
    apiPackage.set("com.epam.java.specialization.trackservice.api")
    modelPackage.set("com.epam.java.specialization.trackservice.api.dto")
    modelNameSuffix.set("Dto")
    typeMappings.set(stringUris)
    // Every public operation is implemented, so no 501 default methods (api-contracts/README.md).
    // containerDefaultToNull: an omitted optional list stays null, so PATCH bodies without genreSlugs pass @Size(min = 1).
    configOptions.set(springOptions + mapOf(
        "interfaceOnly" to "true",
        "skipDefaultInterface" to "true",
        "containerDefaultToNull" to "true",
    ))
}

val openApiGenerateInternal by tasks.registering(org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class) {
    generatorName.set("spring")
    inputSpec.set("$rootDir/../api-contracts/catalog-service/internal-openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/openapi-internal").get().asFile.path)
    apiPackage.set("com.epam.java.specialization.trackservice.internal.api")
    modelPackage.set("com.epam.java.specialization.trackservice.internal.api.dto")
    modelNameSuffix.set("Dto")
    typeMappings.set(stringUris)
    configOptions.set(springOptions + mapOf(
        "interfaceOnly" to "true",
        "skipDefaultInterface" to "true",
        "configPackage" to "org.openapitools.configuration.internal",
    ))
}

val openApiGenerateAuthClient by tasks.registering(org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class) {
    generatorName.set("spring")
    library.set("spring-http-interface")
    inputSpec.set("$rootDir/../api-contracts/auth-service/internal-openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/auth-client").get().asFile.path)
    apiPackage.set("com.epam.java.specialization.trackservice.client.auth.api")
    modelPackage.set("com.epam.java.specialization.trackservice.client.auth.dto")
    modelNameSuffix.set("Dto")
    typeMappings.set(stringUris)
    configOptions.set(springOptions + mapOf(
        "useBeanValidation" to "false",
        "useSpringBuiltInValidation" to "false",
        "configPackage" to "org.openapitools.configuration.authclient",
    ))
}

// Kafka event payloads (api-contracts/catalog-service/asyncapi.yaml), generated as plain models.
val openApiGenerateEvents by tasks.registering(org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class) {
    generatorName.set("spring")
    inputSpec.set("$rootDir/../api-contracts/catalog-service/event-models.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/events").get().asFile.path)
    modelPackage.set("com.epam.java.specialization.trackservice.event")
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
    java.srcDir(layout.buildDirectory.dir("generated/openapi-internal/src/main/java"))
    java.srcDir(layout.buildDirectory.dir("generated/auth-client/src/main/java"))
    java.srcDir(layout.buildDirectory.dir("generated/events/src/main/java"))
}

// gRPC: Java classes generated from the shared contract api-contracts/catalog-service/grpc/*.proto into build/.
// protoc and the grpc-java plugin use the versions managed by the Spring Boot BOM, the same as the runtime jars.
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

tasks.compileJava { dependsOn(tasks.openApiGenerate, openApiGenerateInternal, openApiGenerateAuthClient, openApiGenerateEvents) }
