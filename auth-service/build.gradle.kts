plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.25.0"
}

group = "com.epam.java.specialization"
version = "0.0.1-SNAPSHOT"
description = "auth-service"

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
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    compileOnly("org.projectlombok:lombok:1.18.48")
    annotationProcessor("org.projectlombok:lombok:1.18.48")

    testCompileOnly("org.projectlombok:lombok:1.18.48")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.48")

}

tasks.withType<Test> {
    useJUnitPlatform()
}

val springServerOptions = mapOf(
    "interfaceOnly" to "true",
    "useSpringBoot4" to "true",
    "useJackson3" to "true",
    "useTags" to "true",
    "useBeanValidation" to "true",
    "useSpringBuiltInValidation" to "true",
    "openApiNullable" to "false",
    "skipDefaultInterface" to "true",
    "documentationProvider" to "none",
    "annotationLibrary" to "none",
    "generateJsonIncludeAnnotations" to "false",
    "generateJsonSetterNullsAnnotations" to "false",
)
val stringUris = mapOf("URI" to "String")

openApiGenerate {
    generatorName.set("spring")
    inputSpec.set("$rootDir/../api-contracts/auth-service/openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
    apiPackage.set("com.epam.java.specialization.authservice.api")
    modelPackage.set("com.epam.java.specialization.authservice.api.dto")
    modelNameSuffix.set("Dto")
    typeMappings.set(stringUris)
    configOptions.set(springServerOptions)
}

val openApiGenerateInternal by tasks.registering(org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class) {
    generatorName.set("spring")
    inputSpec.set("$rootDir/../api-contracts/auth-service/internal-openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/openapi-internal").get().asFile.path)
    apiPackage.set("com.epam.java.specialization.authservice.internal.api")
    modelPackage.set("com.epam.java.specialization.authservice.internal.api.dto")
    modelNameSuffix.set("Dto")
    typeMappings.set(stringUris)
    configOptions.set(springServerOptions + ("configPackage" to "org.openapitools.configuration.internal"))
}

sourceSets.main {
    java.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/java"))
    java.srcDir(layout.buildDirectory.dir("generated/openapi-internal/src/main/java"))
}
tasks.compileJava { dependsOn(tasks.openApiGenerate, openApiGenerateInternal) }
