plugins {
    buildsrc.conventions.`kotlin-jvm`
    buildsrc.conventions.`maven-publish`
    buildsrc.conventions.`dokka-docs-share`
}

dependencies {
    implementation(projects.kotlinxKnitTest)
    implementation(projects.kotlinxKnitDokka)

    implementation(libs.slf4j.api)

    implementation(libs.freemarker)
    implementation(libs.dokka.core)

    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.xml)

    testImplementation(kotlin("test-junit5"))

    dokkaHtmlDocs(projects.kotlinxKnitTest)
}

publishing {
    publications {
        register<MavenPublication>("dokkaPlugin") {
            artifactId = "knit-core"
            from(components["java"])
        }
    }
}

tasks.test {
    dependsOn(configurations.dokkaHtmlDocs)
}
