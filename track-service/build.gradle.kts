plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.25.0"
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
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-brave")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.github.resilience4j:resilience4j-spring-boot4:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-retry:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-bulkhead:$resilience4jVersion")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-micrometer-tracing-test")
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")
    testCompileOnly("org.projectlombok:lombok:1.18.48")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.48")
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
    configOptions.set(springOptions + mapOf("interfaceOnly" to "true", "skipDefaultInterface" to "false"))
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
tasks.compileJava { dependsOn(tasks.openApiGenerate, openApiGenerateInternal, openApiGenerateAuthClient, openApiGenerateEvents) }
