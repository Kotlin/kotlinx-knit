# Knit tool

This is a simple tool that produces Kotlin source example files and tests from markdown documents
with embedded snippets of Kotlin code. It also includes links to the documentation website into the documents 
and has a few other helpful markdown-management features.

## Setup

Knit is Gradle plugin that is added to he `build.gradle` in the following way:

```groovy
buildscript {
    dependencies {
        classpath "org.jetbrains.kotlinx:kotlinx-knit:0.1"
    }
}
                    
apply plugin: 'kotlinx-knit'
```               

### API documentation setup

In order to general links to project's API documentation this documentation must be builds using `dokka` in
markdown format and website's root must be configured as shown below: 

```
knit {          
    // Required parameter
    siteRoot = "https://example.com"  // website with project's API documentation
    // Option parameters
    moduleRoots = ["."] // list directories that contain project modules (subdir name == module name)
    moduleMarkers = ["build.gradle", "build.gradle.kts"] // marker files that distinguish module directories
    moduleDocs = "build/dokka" // where documentation is build into 
}                       
                                                      
// Build API docs with dokka before running knit 
knitPrepare.dependsOn rootProject.getTasksByName("dokka", true)
``` 

### Optional parameters

Additional optional parameters can be specified via `knit { ... }` DSL with the defaults as shown below.

```groovy
knit {
    rootDir = project.rootDir // project root dir
    // Custom set of markdown files to process
    files = fileTree(project.rootDir) {
        include '**/*.md'
        exclude '**/build/**'
        exclude '**/.gradle/**'
    }
}
```
