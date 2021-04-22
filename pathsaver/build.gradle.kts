plugins {
    java
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.1")
    implementation("org.jetbrains.dokka:dokka-base:1.4.30")
    compileOnly("org.jetbrains.dokka:dokka-core:1.4.30")
}

publishing {
    publications {
        register<MavenPublication>("dokkaPlugin") {
            artifactId = "dokka-pathsaver-plugin"
            from(components["java"])
        }
    }
}
