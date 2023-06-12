includeBuild("gradle-plugin") {
    dependencySubstitution {
        substitute(module("com.xebia:gradle-plugin:1.0.0")).using(project(":"))
    }
}
includeBuild("compiler-plugin")

include(":lib")
include(":annotation")
