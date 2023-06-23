/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("org.jetbrains.dokka")
    id("maven-publish")
}

val version_ical4j = "3.2.11"

android {
    compileSdk = 33

    namespace = "at.bitfire.ical4android"

    defaultConfig {
        minSdk = 21        // Android 5.0

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "version_ical4j", "\"${version_ical4j}\"")

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

    buildFeatures {
        buildConfig = true
    }

    sourceSets["main"].java {
        srcDir("${projectDir}/src/main/java")
        srcDir("${rootDir}/opentasks-contract/src/main/java")
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

configurations {
    all {
        exclude(group = "org.codehaus.groovy", module = "groovy-dateutil")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.21")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")

    api("org.mnode.ical4j:ical4j:${version_ical4j}") {
        // exclude modules which are in conflict with system libraries
        exclude(group = "commons-logging")
        exclude(group = "org.json", module = "json")
        // exclude groovy because we don"t need it
        exclude(group = "org.codehaus.groovy", module = "groovy")
        exclude(group = "org.codehaus.groovy", module = "groovy-dateutil")
    }
    // ical4j requires newer Apache Commons libraries, which require Java8. Force latest Java7 versions.
    // noinspection GradleDependency
    api("org.apache.commons:commons-collections4") {
        version {
            strictly("4.2")
        }
    }
    // noinspection GradleDependency
    api("org.apache.commons:commons-lang3:3.8.1") {
        version {
            strictly("3.8.1")
        }
    }

    // noinspection GradleDependency
    implementation("commons-io:commons-io:2.6")

    implementation("org.slf4j:slf4j-jdk14:2.0.3")
    implementation("androidx.core:core-ktx:1.10.0")

    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("io.mockk:mockk-android:1.13.4")
    testImplementation("junit:junit:4.13.2")
}