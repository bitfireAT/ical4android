/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.dokka)
    `maven-publish`
}

android {
    compileSdk = 34

    namespace = "at.bitfire.ical4android"

    defaultConfig {
        minSdk = 23        // Android 6

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "version_ical4j", "\"${libs.versions.ical4j.get()}\"")

        aarMetadata {
            minCompileSdk = 29
        }
    }

    compileOptions {
        // ical4j >= 3.x uses the Java 8 Time API
        isCoreLibraryDesugaringEnabled = true
    }
    kotlin {
        jvmToolchain(21)
    }

    buildFeatures.buildConfig = true

    sourceSets["main"].apply {
        kotlin {
            srcDir("${projectDir}/src/main/kotlin")
        }
        java {
            srcDir("${rootDir}/opentasks-contract/src/main/java")
        }
    }

    packaging {
        resources {
            excludes += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/*.md")
        }
    }

    lint {
        disable += listOf("AllowBackup", "InvalidPackage")
    }

    publishing {
        // Configure publish variant
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

publishing {
    // Configure publishing data
    publications {
        register("release", MavenPublication::class.java) {
            groupId = "com.github.bitfireAT"
            artifactId = "ical4android"
            version = System.getenv("GIT_COMMIT")

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

configurations.forEach {
    // exclude modules which are in conflict with system libraries
    it.exclude("commons-logging")
    it.exclude("org.json", "json")

    // exclude groovy because we don"t need it, and it needs API 26+
    it.exclude("org.codehaus.groovy", "groovy")
    it.exclude("org.codehaus.groovy", "groovy-dateutil")
}

dependencies {
    implementation(libs.kotlin.stdlib)
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(libs.androidx.core)
    api(libs.ical4j) {
        // Get rid of unnecessary transitive dependencies
        exclude(group = "commons-validator", module = "commons-validator")
    }
    // Force latest version of commons libraries
    implementation(libs.commons.codec)
    implementation(libs.commons.lang)
    implementation(libs.slf4j)       // ical4j logging over java.util.Logger

    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.mockk.android)
    testImplementation(libs.junit)
}
