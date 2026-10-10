plugins {
    `java-library`
    jacoco
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation(libs.jackson)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    errorprone(libs.errorprone)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Quality checks, all run by `./gradlew check`. The same block is in app/build.gradle.kts.
tasks.withType<JavaCompile>().configureEach {
    // All warnings fail the build, except "serial": nothing here is ever serialized.
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial", "-Werror"))
}
spotless {
    java {
        palantirJavaFormat(libs.versions.palantir.get())
    }
}
jacoco {
    toolVersion = libs.versions.jacoco.get()
}
tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
