plugins {
    buildsrc.conventions.`kotlin-jvm`
    buildsrc.conventions.`maven-publish`
}

dependencies {
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.kotlin)
    implementation(libs.jackson.xml)

    implementation(libs.dokka.base)
    implementation(libs.dokka.templatingPlugin)
    compileOnly(libs.dokka.core)

    testImplementation(kotlin("test-junit5"))
}

publishing {
    publications {
        register<MavenPublication>("dokkaPlugin") {
            artifactId = "kotlinx-knit-dokka"
            from(components["java"])
        }
    }
}
