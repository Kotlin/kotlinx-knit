package buildsrc.config

import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.named

/** Mark this [Configuration] as one that will be consumed by other subprojects. */
fun Configuration.asProvider() {
    isVisible = false
    isCanBeResolved = false
    isCanBeConsumed = true
}

/** Mark this [Configuration] as one that will consume (also known as 'resolving') artifacts from other subprojects */
fun Configuration.asConsumer() {
    isVisible = false
    isCanBeResolved = true
    isCanBeConsumed = false
}


fun AttributeContainer.dokkaHtmlDocsAttributes(objects: ObjectFactory) {
    attribute(CATEGORY_ATTRIBUTE, objects.named("kotlinx-dokka-docs"))
    attribute(USAGE_ATTRIBUTE, objects.named("html"))
}
