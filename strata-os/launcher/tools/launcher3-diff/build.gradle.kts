// Compares Strata's AppSearch with AOSP Launcher3's StringMatcherUtility on the JVM.
// Needs a Launcher3 source checkout: see README.md.
plugins {
    kotlin("jvm") version "2.0.21"
}

val strataSrc = file("../../app/src/main/java")
val launcher3Src = providers.gradleProperty("launcher3Src").orNull?.let(::file)
    ?: throw GradleException("Pass -Plauncher3Src=/path/to/Launcher3/src (see README.md)")

sourceSets {
    main {
        // Only Strata's search file, compiled exactly as the app ships it.
        kotlin.srcDir(strataSrc)
        kotlin.include("com/strataos/launcher/data/AppSearch.kt")
    }
    test {
        // The upstream matcher, unmodified, plus two small JVM stand-ins for Android types.
        java.srcDir(launcher3Src)
        java.include("android/**", "androidx/**")
        java.include("com/android/launcher3/search/StringMatcherUtility.java")
        java.include("com/android/launcher3/util/IntArray.java")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    testLogging {
        events("passed", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
