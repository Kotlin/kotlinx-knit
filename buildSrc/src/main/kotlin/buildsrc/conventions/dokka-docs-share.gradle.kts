package buildsrc.conventions

import buildsrc.config.asConsumer
import buildsrc.config.asProvider
import buildsrc.config.dokkaHtmlDocsAttributes

// create configurations for sharing files between subprojects in a Gradle-compatible manner.
// Subprojects that apply this plugin must manually add/consume artifacts to the necessary Gradle configurations.

val dokkaHtmlDocs by configurations.registering {
    description = "Retrieve Dokka HTML Documentation from other subprojects"
    asConsumer()
    attributes { dokkaHtmlDocsAttributes(objects) }
}

val dokkaHtmlDocsElements by configurations.registering {
    description = "Provide Dokka HTML Documentation to other subprojects"
    asProvider()
    attributes { dokkaHtmlDocsAttributes(objects) }
}
