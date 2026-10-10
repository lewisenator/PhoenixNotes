plugins {
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
    implementation(project(":signing"))
    implementation(libs.flatlaf)
    implementation(libs.picocli)
    errorprone(libs.errorprone)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "com.lewisenator.phoenixnotes.Main"
    // FlatLaf loads a small native library for window decorations; Java 24+ asks apps to opt in.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

// Quality checks, all run by `./gradlew check`. The same block is in signing/build.gradle.kts,
// except for the coverage minimum: the app includes Swing code that's thin and lightly tested.
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
    jvmArgs("--enable-native-access=ALL-UNNAMED") // Tests open real windows; see application above.
    finalizedBy(tasks.jacocoTestReport)
}
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
