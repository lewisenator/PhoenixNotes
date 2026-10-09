plugins {
    // Downloads a matching JDK when the toolchain a module asks for isn't installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "PhoenixNotes"

dependencyResolutionManagement {
    // Repositories are declared once, here; modules can't add their own.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

include("app", "signing")
