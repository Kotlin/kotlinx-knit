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