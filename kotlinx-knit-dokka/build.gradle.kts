import kotlinx.knit.build.*

plugins {
    buildsrc.conventions.`kotlin-jvm`
    signing
    `maven-publish`
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
            artifactId = "dokka-pathsaver-plugin"
            from(components["java"])
            mavenCentralArtifacts(project, project.sourceSets.main.allSource)
        }
    }

    mavenCentralMetadata()
    mavenRepositoryPublishing(project)
    publications.withType(MavenPublication::class).configureEach {
        signPublicationIfKeyPresent(this)
    }
}

val test: Task by tasks.getting {
    dependsOn(rootProject.tasks.findByPath(":kotlinx-knit-test:dokka"))
    dependsOn(rootProject.tasks.findByPath(":kotlinx-knit-test:dokkaHtml"))
}
