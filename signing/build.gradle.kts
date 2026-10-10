plugins {
    `java-library`
    application
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
    implementation(libs.picocli)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    errorprone(libs.errorprone)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "com.lewisenator.phoenixnotes.signing.SigningTool"
}

// ./gradlew keysInit, keysRotate, keysBreakGlass: manage the signing keys (see the README).
// They use the op (1Password) and gh (GitHub) commands, which must be installed and signed in.
mapOf(
    "keysInit" to "init",
    "keysRotate" to "rotate",
    "keysBreakGlass" to "break-glass",
).forEach { (task, command) ->
    tasks.register<JavaExec>(task) {
        group = "keys"
        description = "Runs `signing keys $command`."
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass = application.mainClass
        workingDir = rootDir
        args("keys", "--keys", "trust/keys.json", command)
        standardInput = System.`in` // break glass asks for confirmation
    }
}

// Quality checks, all run by `./gradlew check`. The same block is in app/build.gradle.kts.
tasks.withType<JavaCompile>().configureEach {
    // All warnings fail the build, except "serial" (nothing is ever serialized) and "processing"
    // (it flags annotations no processor handles, like picocli's, which are read at runtime).
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing", "-Werror"))
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
