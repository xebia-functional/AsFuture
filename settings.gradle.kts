includeBuild("gradle-plugin") {
    dependencySubstitution {
        substitute(module("com.xebia:gradle-plugin:1.0.0")).using(project(":"))
    }
}
include("compiler-plugin")
include(":annotation")
