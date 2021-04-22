import kotlinx.knit.build.allSource
import kotlinx.knit.build.mavenCentralArtifacts
import org.jetbrains.dokka.gradle.*

apply(plugin = "org.jetbrains.dokka")

val dokka by tasks.getting(DokkaTask::class) {
    outputFormat = "jekyll"
    outputDirectory = "$buildDir/dokka"
}

val dokkaHtml by tasks.creating(DokkaTask::class) {
    outputFormat = "html"
    outputDirectory = "$buildDir/dokkaHtml"
}

publishing {
    publications {
        create<MavenPublication>("kotlinxKnitTest") {
            from(components["java"])
            mavenCentralArtifacts(project, project.sourceSets.main.allSource)
        }
    }
}
