plugins {
    id("phoenixnotes.java-conventions")
    application
}

dependencies {
    implementation(project(":signing"))
}

application {
    mainClass = "com.lewisenator.phoenixnotes.Main"
}
