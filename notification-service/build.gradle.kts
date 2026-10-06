plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.25.0"
}

group = "com.epam.java.specialization"
version = "0.0.1-SNAPSHOT"
description = "notification-service"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$rootDir/../api-contracts/notification-service/openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
    apiPackage.set("com.epam.java.specialization.notificationservice.api")
    modelPackage.set("com.epam.java.specialization.notificationservice.api.dto")
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

sourceSets.main { java.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/java")) }
tasks.compileJava { dependsOn(tasks.openApiGenerate) }
