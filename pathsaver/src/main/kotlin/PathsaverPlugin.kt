package kotlinx.knit.pathsaver

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.knit.pathsaver.DocumentableType.Companion.type
import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.DokkaConfiguration.DokkaModuleDescription
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.templating.parseJson
import org.jetbrains.dokka.base.templating.toJsonString
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.*
import org.jetbrains.dokka.model.doc.DocumentationLink
import org.jetbrains.dokka.pages.DriResolver
import org.jetbrains.dokka.pages.RendererSpecificResourcePage
import org.jetbrains.dokka.pages.RenderingStrategy
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.plugability.DokkaPluginApiPreview
import org.jetbrains.dokka.plugability.PluginApiPreviewAcknowledgement
import org.jetbrains.dokka.templates.TemplateProcessingStrategy
import org.jetbrains.dokka.templates.TemplatingPlugin
import org.jetbrains.dokka.transformers.documentation.DocumentableTransformer
import org.jetbrains.dokka.transformers.pages.PageTransformer
import java.io.File
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

const val LINK_INDEX_FILE = "paths-index.json"

/**
 * Pathsaver plugin is a Dokka plugin (both for single- and multi-module documentation) that traverses the [Documentable] tree
 * and extracts all [DRI]s from the Documentables and their documentation and saves them as a JSON file called [LINK_INDEX_FILE].
 */
class PathsaverPlugin : DokkaPlugin() {
    private val dokkaBasePlugin by lazy { plugin<DokkaBase>() }
    private val templatingPlugin by lazy { plugin<TemplatingPlugin>() }

    val transformer by extending {
        CoreExtensions.documentableTransformer with DriExtractor order { after(dokkaBasePlugin.emptyPackagesFilter) }
    }

    val preprocessor by extending {
        CoreExtensions.pageTransformer providing ::PathSaver
    }

    val linkIndexTemplateProcessingStrategy by extending {
        templatingPlugin.templateProcessingStrategy providing ::LinkIndexTemplateProcessingStrategy order {
            before(templatingPlugin.fallbackProcessingStrategy)
        }
    }

    @OptIn(DokkaPluginApiPreview::class)
    override fun pluginApiPreviewAcknowledgement(): PluginApiPreviewAcknowledgement {
        return PluginApiPreviewAcknowledgement
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

/**
 * This class adds a [RendererSpecificResourcePage] called [LINK_INDEX_FILE] which is (depending on the context)
 * a [LinkIndexTemplate] in partial tasks and a `List<[LinkIndexEntry]>` in regular tasks
 */
class PathSaver(private val context: DokkaContext) : PageTransformer {
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
                                LinkIndexEntry(
                                        dri = driInfo.dri.copy(extra = context.configuration.moduleName),
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
                    mapper.writeValueAsString(getLinks(resolver))
                }
        )
    }
}

/**
 * Maps [Documentable]s to enum objects (otherwise we would have to serialize the whole Documentable) and provides ordering
 * for the KnitApiParser of the preferred links
 */
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

/**
 * Represents one link entry, eg. a function parameter with its DRI and its location relative to the module
 */
data class LinkIndexEntry(val dri: DRI, val sourceSet: List<String>, val location: String, val type: DocumentableType)

class LinkIndexTemplateProcessingStrategy(private val context: DokkaContext) : TemplateProcessingStrategy {
    private val fragments = ConcurrentHashMap<String, List<LinkIndexEntry>>()

    private fun canProcess(file: File): Boolean =
            file.extension == "json" && file.name == LINK_INDEX_FILE

    override fun process(input: File, output: File, moduleContext: DokkaModuleDescription?): Boolean {
        val canProcess = canProcess(input)
        if (canProcess) {
            runCatching { parseJson<List<LinkIndexEntry>>(input.readText()) }
                .getOrNull()
                ?.let { links ->
                    moduleContext
                        ?.relativePathToOutputDirectory
                        ?.relativeToOrSelf(context.configuration.outputDir)
                        ?.let { key ->
                            fragments[key.toString()] = links
                        }
                } ?: fallbackToCopy(input, output)
        }
        return canProcess
    }

    override fun finish(output: File) {
        if (fragments.isNotEmpty()) {
            val content = fragments.entries.flatMap { (moduleName, links) ->
                links.map {
                    if (it.location.isURL())
                        it
                    else
                        it.copy(location = "$moduleName/${it.location}")
                }
            }.let(::toJsonString)

            output.mkdirs()
            output.resolve(LINK_INDEX_FILE).writeText(content)
        }
    }

    private fun String.isURL() = runCatching { URL(this) }.isSuccess
    private fun fallbackToCopy(input: File, output: File) {
        context.logger.warn("Falling back to just copying file for ${input.name} even thought it should process it")
        input.copyTo(output, overwrite = true)
    }
}
