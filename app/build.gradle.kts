plugins {
    application
    jacoco
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
}

// CI passes the release version, 1.0.<commit count>; local builds are "dev".
version = providers.gradleProperty("releaseVersion").getOrElse("dev")

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

// A release is one self-contained jar: updates download exactly one file and run it with `java -jar`.
tasks.jar {
    from({ configurations.runtimeClasspath.get().map { zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
        exclude("module-info.class", "META-INF/versions/*/module-info.class")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "Main-Class" to application.mainClass.get(),
            "Implementation-Version" to project.version,
            "Enable-Native-Access" to "ALL-UNNAMED",
        )
    }
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
