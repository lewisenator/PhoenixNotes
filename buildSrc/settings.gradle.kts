// buildSrc is its own small build; this lets its plugins read the main build's version catalog.
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
