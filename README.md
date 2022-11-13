# Knit tool

[![Kotlin Experimental](https://kotl.in/badges/experimental.svg)](https://kotlinlang.org/docs/components-stability.html)
[![JetBrains incubator project](https://jb.gg/badges/incubator.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Apache license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)
[![Download](https://img.shields.io/maven-central/v/org.jetbrains.kotlinx/kotlinx-knit?versionPrefix=0.3&versionSuffix=.0)](https://search.maven.org/artifact/org.jetbrains.kotlinx/kotlinx-knit/0.4.0/pom)

Kotlin source code documentation management tool.

This is a tool that produces Kotlin source example files and tests from Markdown documents
with embedded snippets of Kotlin code. It also helps to add links to the API documentation website into the
documents and has a few other helpful markdown-management features.

Knit tool is a [Gradle](https://gradle.org/) plugin that processes markdown (`.md`) files and Kotlin (`.kt`/`.kts`) files 
with markdown KDoc comments, updates them, and writes additional example and test files, 
which are committed to the VCS. The overall workflow is:

1. Write or update documentation in markdown files (`.md`) or in source files (`.kt`/`.kts`).
2. Run `gradlew knit` to update markdown and source files, generate additional source code samples and tests.
3. Commit to VCS.
4. Generated files are automatically verified on subsequent project builds.

Knit does not really parse Markdown format or HTML, but understands certain Knit markup patterns and _directives_.
Directives in markdown files must always start at the beginning of the line and have the following general format for
single-line directives:

    <!--- <directive> [<parameters>] -->

or the following format for multi-line directives:

    <!--- <directive> [<parameters>] 
    <text> 
    -->

Directives look like HTML comments, so their contents are not visible when the markdown is rendered by
regular tools. Specific markup patterns and directives supported by Knit are explained in the [Features](#features) section.
For inclusion of directives into Kotlin source (`.kt`/`.kts`) files see 
the [Kotlin Source Comments](#kotlin-source-comments) section.  

## Contents

<!--- TOC -->

* [Setup](#setup)
  * [Tasks](#tasks)
  * [Optional parameters](#optional-parameters)
* [Knit properties](#knit-properties)
* [Features](#features)
  * [Example files](#example-files)
    * [Explicit knit directive](#explicit-knit-directive)
    * [Merging code pieces](#merging-code-pieces)
    * [Custom Knit template](#custom-knit-template)
    * [Include directive](#include-directive)
    * [Advanced include](#advanced-include)
    * [Prefix directive](#prefix-directive)
    * [Suffix and back-to-back directives](#suffix-and-back-to-back-directives)
  * [Tests](#tests)
    * [Hidden test](#hidden-test)
    * [Custom test predicate](#custom-test-predicate)
    * [Output comparison mode](#output-comparison-mode)
    * [Test template](#test-template)
  * [Kotlin Source Comments](#kotlin-source-comments)
  * [API references](#api-references)
    * [Dokka setup](#dokka-setup)
  * [Table of contents](#table-of-contents)

<!--- END -->

## Setup

Knit is a Gradle plugin that is published to [Maven Central](https://search.maven.org/artifact/org.jetbrains.kotlinx/kotlinx-knit). 
Add it to the `build.gradle` in the following way:

```groovy        
buildscript {
    dependencies {
        classpath "org.jetbrains.kotlinx:kotlinx-knit:0.4.0"
    }
}
                    
apply plugin: 'kotlinx-knit'
```               

> The build must apply 'kotlin' plugin or, at least, 'base' plugin before 'kotlinx-knit'.

### Tasks

Knit plugin registers the following tasks:

* `knit` &mdash; updates markdown files, samples, and tests.
* `knitCheck` &mdash; checks that all the files are up-to-date and fail the build if not;
  it is automatically added as dependency to `check` task and thus is performed on `build` if the `base` plugin is applied.
* `knitPrepare` &mdash; does nothing, but is added as a dependency to both `knit` and `knitCheck` and a
  common place to register all prerequisite tasks like `dokka` (see [Dokka setup](#dokka-setup))    

### Optional parameters

Additional optional parameters can be specified via `knit { ... }` DSL with the defaults as shown below.

```groovy
knit {
    rootDir = project.rootDir // project root dir
    // Custom set of input files to process (default as shown below)
    files = fileTree(project.rootDir) {
        include '**/*.md'
        include '**/*.kt'
        include '**/*.kts'
        exclude '**/build/**'
        exclude '**/.gradle/**'
    }
    defaultLineSeparator = '\n' // line separator used for newly generated files
}
```

## Knit properties

Some Knit [features](#features) use additional properties. These properties are stored in `knit.properties` file
that is located in the same directory as the corresponding markdown file. Knit tool also looks for the properties
file in all the parent directories up to the root directory (see [Optional parameters](#optional-parameters)).
This allows for fine-grained control and inheritance of properties in different parts of the project. 

All paths that are specified in the property files are resolved relative to the directory of the corresponding
property file. Paths specified in markdown files are relative to the corresponding markdown file, too.  

## Features

All Knit features are driven by feature-specific patterns and directives and can be used independently. 

### Example files 

Knit can generate Kotlin source examples from the code that is being quoted in the documentation. 
To set it up you need to specify at least the following two properties in [`knit.properties`](#knit-properties),
for example:

```properties 
knit.dir=src/test/kotlin/example/
knit.package=com.example
```                       

The `knit.dir` must specify the relative path to the directory for the examples (note that it must end with `/`)
and `knit.package` must specify the package name for the example files. 
The directory is usually marked as or located inside the project's test sources and gets
compiled when the project is built. This way, Knit tool helps to ensure that all the code in
the documentation is syntactically correct and compiler without errors.

In the markdown file Knit collects together all the `kotlin` sources in the markdown that are surrounded
by a triple backticks like this:

    ```kotlin
    fun foo() {}
    ```
    
The Knit que to generate example source code is a markdown reference in round braces to the file that needs
to be generated. It must start with the value of `knit.dir` property (verbatim) followed by the example's
file name, for example:       

    > You can get the full code [here](src/test/kotlin/example/example-basic-01.kt).
    
The name of the example file must match a specific pattern. By default, this pattern's regex is
`example-[a-zA-Z0-9-]+-##\\.kt`. It can be overridden via `knit.pattern` [property](#knit-properties).
The sequence of hashes (`#`) in the pattern matches any alphanumeric sequence and causes the examples to
be automatically consecutively numbered inside the markdown file. For example, you can add a
new section of code at the beginning of the document and write in the markdown file:

    > You can get the full code [here](src/test/kotlin/example/example-basic-new.kt).
    
After running `knit` task this line in the markdown file will get updated to:

    > You can get the full code [here](esrc/test/kotlin/example/example-basic-01.kt).
    
The corresponding Kotlin file is also automatically created or updated as needed by `knit` task
and will look like this:

```kotlin
// This file was automatically generated from example-basic.md by Knit tool. Do not edit.
package com.example.exampleBasic01

fun foo() {}                                    
```

#### Explicit knit directive

A `KNIT` directive can be used to trigger generation of an example file instead of providing a readable reference to a file. 
In this case, only an example file name shall be specified (without a path).
An example file will be written to the `knit.dir` [property](#knit-properties) directory. 
The name of the file shall still match the `knit.pattern` [property](#knit-properties) pattern, and 
it will be automatically numbered as explained in the previous section. 
For example: 

    ```kotlin
    fun foo() {}
    ```
    
    <!--- KNIT example-knit-01.kt --> 
    
#### Merging code pieces

All triple-backquoted Kotlin sections are merged together and are output to the Kotlin source file when the next
Knit pattern in encountered. This way, documentation can be written fluently, explaining
functions as they are introduced. For example, the following markdown:

    This function computes the square of the given integer:
    
    ```kotlin 
    fun sqr(x: Int) = x * x
    ```                                            
    
    We can use to print the square:
    
    ```kotlin
    fun main() {
        println(sqr(5))
    }
    ```

    > You can get the full code [here](src/test/kotlin/example/example-merge-01.kt).
    
Produces the following Kotlin source code when `knit` task is run:

```kotlin 
// This file was automatically generated from example-merge.md by Knit tool. Do not edit.
package com.example.exampleMerge01

fun sqr(x: Int) = x * x

fun main() {
    println(sqr(5))
}
```                                         

#### Custom Knit template

The header of this generated example file can be configured by specifying `knit.include` [property](#knit-properties)
that contains a path to the [FreeMarker](https://freemarker.apache.org/) template file. The default template is:

```freemarker
// This file was automatically generated from ${file.name} by Knit tool. Do not edit.
package ${knit.package}.${knit.name}
```    
  
Each example file gets its unique package name where `${knit.package}` part is taken from [properties](#knit-properties)
and `${knit.name}` part is automatically generated by camel-casing the example file name. This allows to have
a number of similar examples in the same markdown file that might, for example, define different versions
of the function with the same name so that they could still be compiled, because they end up in different packages.

You can use arbitrary `knit.xxx` [properties](#knit-properties) in the template and introduce your own properties
so that you can reuse the same template in multiple kinds of documents in your project. The `knit.package` 
property is not special in any way.

#### Include directive

Sometimes it is necessarily to define example-specific additions (like Kotlin `import` lines) that should
not be visible to the readers of documentation to avoid distraction. For example, the documentation
for Kotlin's [`kotlin.system.exitProcess`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.system/exit-process.html)
function might have the following example piece of code:

```kotlin
fun exit(): Nothing = exitProcess(0)
```

It will not compile by itself. In order to generate a proper compilable example file we'd use an `INCLUDE` Knit
directive before this example. The markdown documentation looks like this:

    <!--- INCLUDE
    import kotlin.system.*
    -->
    
    ```kotlin 
    fun exit(): Nothing = exitProcess(0)
    ```                         
    
    > You can get the full code [here](src/test/kotlin/example/example-include-01.kt).
    
The Knit directive is like HTML comment, so the reader of this specific piece of documentation will not
see the `import` line, but the generated source-code example file will include it to get compiled properly:

```kotlin
// This file was automatically generated from example-include.md by Knit tool. Do not edit.
package com.example.exampleInclude01

import kotlin.system.*

fun exit(): Nothing = exitProcess(0)
```      

#### Advanced include

A single piece of code can be included into multiple examples (as opposed to the next example only)
by specifying regex pattern of the example file name right after the `INCLUDE` directive as its parameter. 

With the pattern the `INCLUDE` directive can also be specified on a single line, without the
code inside it. In this case, the code to be included is taken from the previously triple-backquoted
Kotlin source code before it. This way, the code snippet can be introduced and shown to the reader of
the documentation and then included into the several subsequent examples.  

#### Prefix directive

If you need to prefix the example file with certain lines, for example, to specify a file-level annotation, then
write the block of code normally and follow it with the `PREFIX` directive. All the previously written code will
be added to the beginning of the resulting example file instead of the end of it. So this input:

    ```kotlin 
    @file:JvmName("ExampleKt")
    ```      
    
    <!--- PREFIX -->
    
    ```kotlin
    fun example() {}
    ```                   
    
    > You can get the full code [here](example-prefix/example-prefix-01.kt).
    
Produces this example file:

```kotlin
@file:JvmName("ExampleKt")
// This file was automatically generated from example-prefix.in.md by Knit tool. Do not edit.
package com.example.examplePrefix01

fun example() {}
```          

Just like the `INCLUDE` directive you can put the code inside the `PREFIX` directive, so that the reader
does not see it (because it is a comment), but it affects the resulting example file. A parameter with the file
name pattern can be also added to the `PREFIX` directive to affect multiple examples. 

#### Suffix and back-to-back directives

The `SUFFIX` directive queues a piece of code to be added to the end of the example file and supports all the same features
as `INCLUDE` and `PREFIX`, including patterns. This way, using a combination of `INCLUDE` and `SUFFIX`, 
a specific common scaffolding can be defined around several code examples matching a specific name pattern.
When multiple directives are written back-to-back they can be laid out as a single HTML comment, using `----- `
to end one directive and begin the next one. 

    <!--- INCLUDE .*print.*
    fun main() { 
    ----- SUFFIX .*print.*
        .let { println(it) }
    }    
    -->
    
    The following expression:
    
    ```kotlin
    "OW".replace('W', 'K')
    ```
    
    > You can get the full code [here](src/test/kotlin/example/example-print-01.kt).
 

### Tests

Knit tool can also automatically generate tests. To set it up you need to be generating [example files](#example-files) first
and then add the following properties in [`knit.properties`](#knit-properties):
                                                 
```properties 
test.dir=src/test/kotlin/example/test/
test.package=com.example.test
```                       
 
Here `test.dir` specified the directory where the Kotlin test code is generated too and `test.package` specifies
the package. In the beginning of the markdown file you specify the name of the test using `TEST_NAME` directive.
There is one test file per the source markdown file with a test-case for each example. After the example
you can place the expected output of the test in triple-quoted `text` block and add `TEST` directive after
it to get the test-case added. For example:

    <!--- TEST_NAME BasicTest --> 
    
    Here is some explanatory text
    
    ```kotlin 
    fun main() {
        println("Hello, world!")
    }
    ```                         
    
    > You can get the full code [here](src/test/kotlin/example/example-basic-01.kt).  
    
    This code prints:
    
    ```text
    Hello, world!
    ```
    
    <!--- TEST -->

Based on these directives, the `knit` task will create `BasicTest.kt` file with the following contents:

```kotlin 
// This file was automatically generated from test-basic.md by Knit tool. Do not edit.
package com.example.test

import org.junit.Test
import kotlinx.knit.test.*

class BasicTest {
    @Test
    fun testExampleBasic01() {
        captureOutput("ExampleBasic01") { com.example.exampleBasic01.main() }.verifyOutputLines(
            "Hello, world!"
        )
    }
}
``` 

The test runs the generated example, assuming it defines `main` function, and verifies the
produced output. Two helper functions `captureOutput` and `verifyOutputLines` are provided in a separate
artifact that you need to add to your test dependencies to compile and run the resulting test:

```groovy
dependencies {
    testImplementation "org.jetbrains.kotlinx:kotlinx-knit-test"
}
``` 

> You don't need this dependency if you use a custom [test template](#test-template) that is using
> your project-specific functions.  

#### Hidden test

If you do not want to include the sample output in the documentation itself, but still want the test
to be generated, then you can include the expected output into the `TEST` directive itself, for example:

    <!--- TEST
    Hello, world!
    -->

#### Custom test predicate

If the output of the sample code can be non-deterministic you'd need to write test verification logic.
If this logic is single-liner, then you can specify the corresponding test predicate directly as
parameter to `TEST` directive operating over `lines: List<String>`, for example, in order
to check that the example had output an integer between 1 and 100 you can write:
 
    <!--- TEST lines.single().toInt() in 1..100 -->   

#### Output comparison mode

A different output comparison mode can be specified by adding the name of the mode after the `TEST` directive. 
The names of the modes are mapped to comparison functions via `test.mode.<mode-name>` properties, with
`verifyOutputLines` function being used as a default. Other modes that are supported out of the box:

* `LINES_START` &mdash; tests that output starts with the specified lines, skipping the rest of line, uses `verifyOutputLinesStart` function.

#### Test template

Generation of the test source code is completely template-based. The default template
is located in [`knit.test.template`](resources/knit.test.template) file and can be overridden
via `test.template` [property](#knit-properties).
You can use arbitrary `test.xxx` [properties](#knit-properties) in the test template.

The default template assumes that example code contains `main()` function and produces some output on the
console. By tweaking the template you can test other kinds of examples in your markdown documentation.  

### Kotlin Source Comments

Knit directives and other Knit-recognized Markdown markup can be embedded into documentation of 
Kotlin source (`.kt`/`.kts`) files. There are several ways to embed Knit markup into Kotlin sources.

Knit markup can be nested inside regular /* ... */ comment block, directives starting at the beginning of the line.
For example:

    /* Include the following snippet into all generated examples
    <!--- INCLUDE .*
    import kotlin.time.*
    -->
    */
    
Knit markup can be specified after `//` line comment, directives separated by one space from the beginning
of the comment. The whitespace character after the `//` start marker is dropped when reading the directive body.

    // Prefix the following example with this annotation
    // <!--- PREFIX
    // @file:OptIn(ExperimentalTime::class)
    // -->
      
Knit markup can be specified inside `/** ... */` KDoc comments, separated by one space from the `*` at the 
beginning of comment lines. The Knit tool does not really parse Kotlin files. It just looks at the `*` character
at the beginning of the line. 

    /**
     * The ultimate answer to life, universe, and everything can be printed like this:
     * ```kotlin
     * fun main() {
     *     println(theAnswer())
     * }
     * ```
     * <!--- KNIT example-kdoc-01.kt -->
     */
     fun theAnswer() = 42        


### API references

Knit tool can add links to project's API documentation, so that you can link to the public classes and
functions similarly to how you do it from KDoc using markdown `[name]` reference syntax.

> This feature is not available inside Kotlin source (`.kt`/`.kts`) files, because API documentation 
> references inside KDoc comments are processed by the Dokka tool.

#### Dokka setup

In order to generate links to project's API documentation this documentation must be built using 
[Dokka](https://github.com/Kotlin/dokka) in either `markdown`, `jekyll`, or `html` formats:

```groovy 
dokka {
    outputFormat = "jekyll" 
    outputDirectory = "$buildDir/dokka"
}
``` 

Website's root for Knit must be configured as shown below: 

```
knit {          
    // Required parameter
    siteRoot = "https://example.com"  // website with project's API documentation without trailing /
    // Optional parameters (do not need specify them if below defaults are Ok) 
    moduleRoots = ["."] // list directories that contain project modules (subdir name == module name)
    moduleMarkers = ["build.gradle", "build.gradle.kts"] // marker files that distinguish module directories
    moduleDocs = "build/dokka" // where documentation is build into relative to module root 
}                       
                                                      
// Build API docs for all modules with dokka before running Knit 
knitPrepare.dependsOn rootProject.getTasksByName("dokka", true)
```          

The modules providing APIs can be stored in separate directories named after the module name. For example,
this project has [`kotlinx-knit-test`](kotlinx-knit-test) module in a separate directory. You can reference
functions and classes declared there using a regular Markdown link syntax and give instructions to Knit 
tool to expand those links like this:

    Here is a link to [captureOutput] function.

    <!--- MODULE kotlinx-knit-test -->
    <!--- INDEX kotlinx.knit.test -->
    <!--- END -->
    
The `MODULE` directive specified the name of the module. Knit looks for the corresponding directory that contains
one of the configured `moduleMarkers` files. This directive is followed by one or more `INDEX` directives that
specify package names. 

When you run `knit` task this markdown gets updated to:

    Here is a link to [captureOutput] function.

    <!--- MODULE kotlinx-knit-test -->
    <!--- INDEX kotlinx.knit.test -->
    [captureOutput]: https://example.com/kotlinx-knit-test/kotlinx.knit.test/capture-output.html
    <!--- END -->
    
Now the link is defined to point to `<siteRoot>/<moduleName>/<package>/<docs-file>`.    

When the documentation is build by the root project of the module, then prepend `/` before the module name in 
the `MODULE` directive:

    <!--- MODULE /kotlinx-knit-test -->

### Table of contents

Knit can generate "Table of contents" for big markdown file that includes references to
all second-level and smaller-level header. Just put `TOC` and `END` directives at the
beginning of the markdown file like this:

    <!--- TOC -->
    <!--- END -->

On the next run of `knit` task the table of contents will get placed in between them,
replacing all the text previously contained there. This README file is
an example markdown file using this feature. See [Contents](#contents) section
in the beginning.
