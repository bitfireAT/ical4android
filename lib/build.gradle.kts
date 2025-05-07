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
    compileSdk = 35

    namespace = "at.bitfire.ical4android"

    defaultConfig {
        minSdk = 23        // Android 6

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "version_ical4j", "\"${libs.versions.ical4j.get()}\"")

        aarMetadata {
            minCompileSdk = 29
        }

        // These ProGuard/R8 rules will be included in the final APK.
        consumerProguardFiles("consumer-rules.pro")
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

    buildTypes {
        release {
            // Android libraries shouldn't be minified:
            // https://developer.android.com/studio/projects/android-library#Considerations
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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

dependencies {
    implementation(libs.kotlin.stdlib)
    coreLibraryDesugaring(libs.android.desugaring)

    implementation(libs.androidx.core)
    api(libs.ical4j)
    implementation(libs.slf4j.jdk)       // ical4j uses slf4j, this module uses java.util.Logger

    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.mockk.android)
    testImplementation(libs.junit)
}