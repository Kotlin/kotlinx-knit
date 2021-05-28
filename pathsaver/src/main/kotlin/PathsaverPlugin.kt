package kotlinx.knit.pathsaver

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.knit.pathsaver.DocumentableType.Companion.type
import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.*
import org.jetbrains.dokka.model.doc.DocumentationLink
import org.jetbrains.dokka.pages.DriResolver
import org.jetbrains.dokka.pages.RendererSpecificResourcePage
import org.jetbrains.dokka.pages.RenderingStrategy
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.transformers.documentation.DocumentableTransformer
import org.jetbrains.dokka.transformers.pages.PageTransformer

const val LINK_INDEX_FILE = "paths-index.json"

/**
 * Pathsaver plugin is a Dokka plugin (single-module only for now) that traverses the [Documentable] tree and extracts all
 * [DRI]s from the Documentables and their documentation and saves them as a JSON file called [LINK_INDEX_FILE].
 */
class PathsaverPlugin : DokkaPlugin() {
    private val dokkaBasePlugin by lazy { plugin<DokkaBase>() }

    val transformer by extending {
        CoreExtensions.documentableTransformer with DriExtractor order { after(dokkaBasePlugin.emptyPackagesFilter) }
    }

    val preprocessor by extending {
        CoreExtensions.pageTransformer with PathSaver
    }
}

data class DRIInfo(val dri: DRI, val documentableType: DocumentableType, val sourceSets: Set<DisplaySourceSet>)

object DriExtractor : DocumentableTransformer {
    private val _drisList: MutableList<DRIInfo> = mutableListOf()
    val drisList: List<DRIInfo>
        get() = _drisList

    override fun invoke(original: DModule, context: DokkaContext) =
            original.also { module ->
                module.withDescendants().forEach { documentable ->
                    val documentableSourceSets = documentable.sourceSets.toDisplaySourceSets()
                    _drisList.add(DRIInfo(documentable.dri, documentable.type(), documentableSourceSets))

                    documentable.documentation.forEach { (_, docNode) ->
                        docNode.children.forEach { tagWrapper ->
                            tagWrapper.withDescendants().forEach { docTag ->
                                if (docTag is DocumentationLink) {
                                    val linkTarget = module.dfs { it.dri == docTag.dri }
                                    _drisList.add(DRIInfo(docTag.dri, linkTarget?.type()
                                            ?: DocumentableType.Unknown, linkTarget?.sourceSets?.toDisplaySourceSets()
                                            ?: documentableSourceSets))
                                }
                            }
                        }
                    }
                }
            }
}

object PathSaver : PageTransformer {
    private val mapper = jacksonObjectMapper()

    override fun invoke(input: RootPageNode): RootPageNode {
        return input.modified(
                children = input.children + createDriPathPage()
        )
    }

    private fun createDriPathPage(
    ): RendererSpecificResourcePage {
        fun getLinks(locationResolver: DriResolver) =
                DriExtractor
                        .drisList
                        .flatMap { driInfo -> driInfo.sourceSets.map { sourceSet -> driInfo to sourceSet } }
                        .mapNotNull { (driInfo, sourceSet) ->
                            locationResolver(driInfo.dri, sourceSet)?.let {
                                LinkIndex(
                                        dri = driInfo.dri,
                                        sourceSet = listOf(sourceSet.name),
                                        location = it,
                                        type = driInfo.documentableType
                                )
                            }
                        }.groupingBy { Pair(it.dri, it.location) }
                        .reduce { _, acc, el -> acc.copy(sourceSet = acc.sourceSet + el.sourceSet) }
                        .values.distinct()

        return RendererSpecificResourcePage(
                name = LINK_INDEX_FILE,
                children = emptyList(),
                strategy = RenderingStrategy.DriLocationResolvableWrite { resolver ->
                    val content = getLinks(resolver)
                    mapper.writeValueAsString(content)
                }
        )
    }
}

enum class DocumentableType {
    Module, Package, Class, Interface, Object, Annotation, TypeAlias, Enum, EnumEntry, Function, TypeParameter, Property, Parameter, Unknown;

    companion object {
        fun Documentable.type(): DocumentableType = when (this) {
            is DModule -> Module
            is DPackage -> Package
            is DClass -> Class
            is DEnum -> Enum
            is DEnumEntry -> EnumEntry
            is DFunction -> Function
            is DInterface -> Interface
            is DObject -> Object
            is DAnnotation -> Annotation
            is DProperty -> Property
            is DParameter -> Parameter
            is DTypeParameter -> TypeParameter
            is DTypeAlias -> TypeAlias
            else -> {
                println("Unrecognized Documentable type: $this")
                Unknown
            }
        }
    }
}

data class LinkIndex(val dri: DRI, val sourceSet: List<String>, val location: String, val type: DocumentableType)
