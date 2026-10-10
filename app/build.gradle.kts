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

// The trusted key chain is built into the app (see docs/decisions/0009-key-rotation.md).
tasks.processResources {
    from(rootProject.file("trust/keys.json"))
}

// A release is one self-contained jar: updates download exactly one file and run it with `java -jar`.
tasks.jar {
    // Build the dependency jars (including :signing) first, then copy their contents in.
    dependsOn(configurations.runtimeClasspath)
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

// This OS's installer, with its own Java runtime: `./gradlew :app:installer` (see
// docs/decisions/0017-installers.md). Each OS builds its own, so CI runs this on all three.
val installerInput = tasks.register<Sync>("installerInput") {
    from(tasks.jar) { rename { "app.jar" } }
    into(layout.buildDirectory.dir("installer-input"))
}
tasks.register<Exec>("installer") {
    group = "distribution"
    description = "Builds this OS's installer into build/installer."
    dependsOn(installerInput)
    val os = System.getProperty("os.name")
    val jdk = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }.get().metadata
    val output = layout.buildDirectory.dir("installer").get().asFile
    executable(jdk.installationPath.file(if (os.startsWith("Windows")) "bin/jpackage.exe" else "bin/jpackage").asFile)
    args(
        "--name", "Phoenix Notes",
        // Installers need a number; local builds are "dev".
        "--app-version", if (version == "dev") "1.0.0" else version,
        "--vendor", "lewisenator",
        "--description", "A notepad that keeps itself up to date.",
        "--input", installerInput.get().destinationDir,
        "--main-jar", "app.jar",
        "--main-class", application.mainClass.get(),
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        // Only the modules the app uses (from jdeps). Unlike jpackage's default, this keeps
        // bin/java, which a handoff uses to start the next version.
        "--add-modules", "java.base,java.desktop,java.net.http,java.sql",
        "--jlink-options", "--strip-debug --no-man-pages --no-header-files",
        "--dest", output,
    )
    when {
        os.startsWith("Mac") -> args(
            "--type", "dmg",
            "--icon", file("src/packaging/icon.icns"),
            "--mac-package-identifier", "com.lewisenator.phoenixnotes",
        )
        os.startsWith("Windows") -> args(
            "--type", "msi",
            "--icon", file("src/packaging/icon.ico"),
            // Installs for the current user only, so no admin rights; upgrades replace it.
            "--win-per-user-install",
            "--win-menu",
            "--win-shortcut",
            "--win-upgrade-uuid", "41c033a6-21ca-4166-af79-810e65df6c62",
        )
        else -> args(
            "--type", "deb",
            "--icon", file("src/packaging/icon.png"),
            "--linux-package-name", "phoenix-notes",
            "--linux-shortcut",
            "--linux-menu-group", "Utility",
        )
    }
    doFirst { output.deleteRecursively() }
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
    // The handoff tests start this app's real jar as a separate process.
    val appJar = tasks.jar.flatMap { it.archiveFile }
    inputs.file(appJar)
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dphoenixnotes.appJar=${appJar.get().asFile}") })
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
