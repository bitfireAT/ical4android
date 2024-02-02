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

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
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
    api(libs.ical4j)
    implementation(libs.slf4j)       // ical4j logging over java.util.Logger

    // ical4j requires newer Apache Commons libraries, which require Java8. Force latest Java7 versions.
    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    api(libs.commons.collections) {
        version {
            strictly(libs.versions.commons.collections.get())
        }
    }
    api(libs.commons.lang3) {
        version {
            strictly(libs.versions.commons.lang.get())
        }
    }
    @Suppress("VulnerableLibrariesLocal", "RedundantSuppression")
    implementation(libs.commons.io)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.mockk.android)
    testImplementation(libs.junit)
}
