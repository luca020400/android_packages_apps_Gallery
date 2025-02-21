/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

import groovy.util.Node
import groovy.util.NodeList
import groovy.xml.XmlParser

plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    compileSdk = 33

    defaultConfig {
        applicationId = "org.lineageos.gallery"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            // Enables code shrinking, obfuscation, and optimization.
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = true

            // Includes the default ProGuard rules files.
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            )
        }
        getByName("debug") {
            // Append .dev to package name so we won't conflict with AOSP build.
            applicationIdSuffix = ".dev"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.0"))

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.9.0")

    // Media3
    implementation("androidx.media3:media3-exoplayer:1.1.0")
    implementation("androidx.media3:media3-ui:1.1.0")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.6.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.6.0")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.15.1")
}

tasks.register("generateBp") {
    val project = project(":app")
    val configuration = project.configurations["debugRuntimeClasspath"]

    val libsBase = File("${project.projectDir.absolutePath}/libs")
    libsBase.deleteRecursively()

    val moduleString = { it: ModuleVersionIdentifier -> "${it.group}:${it.name}:${it.version}" }
    val modulePath =
        { it: ModuleVersionIdentifier -> "${it.group.replace(".", "/")}/${it.name}/${it.version}" }

    val spaces = { it: Int ->
        var ret = ""
        for (i in it downTo 1) {
            ret += ' '
        }
        ret
    }

    val moduleName = { it: Any ->
        when (it) {
            is ModuleVersionIdentifier -> {
                "${rootProject.name}_${it.group}_${it.name}"
            }

            is String -> {
                if (it.contains(":")) {
                    val (group, artifactId) = it.split(":")
                    "${rootProject.name}_${group}_${artifactId}"
                } else {
                    "${rootProject.name}_${it}"
                }
            }

            else -> {
                throw Exception("Invalid `it` type")
            }
        }
    }

    val moduleNameAosp = { it: String ->
        when (it) {
            "androidx.constraintlayout:constraintlayout" -> "androidx-constraintlayout_constraintlayout"
            "com.google.auto.value:auto-value-annotations" -> "auto_value_annotations"
            "com.google.guava:guava" -> "guava"
            "com.google.guava:listenablefuture" -> "guava"
            "org.jetbrains.kotlin:kotlin-stdlib" -> "kotlin-stdlib"
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8" -> "kotlin-stdlib-jdk8"
            "org.jetbrains.kotlinx:kotlinx-coroutines-android" -> "kotlinx-coroutines-android"
            else -> it.replace(":", "_")
        }
    }

    val isAvailableInAosp = { group: String, artifactId: String ->
        when {
            group.startsWith("androidx") -> {
                // We provide our own androidx.{media3,navigation,savedstate} & lifecycle-common
                !group.startsWith("androidx.media3") &&
                        !group.startsWith("androidx.multidex") &&
                        !group.startsWith("androidx.navigation") &&
                        !group.startsWith("androidx.savedstate") &&
                        artifactId != "lifecycle-common"
            }

            group.startsWith("org.jetbrains") -> true
            group == "com.google.auto.value" -> true
            group == "com.google.guava" -> true
            group == "junit" -> true
            else -> false
        }
    }

    // Update app/Android.bp
    File("${project.projectDir.absolutePath}/Android.bp").let { file ->
        // Read dependencies
        val dependencies = "${spaces(8)}// DO NOT EDIT THIS SECTION MANUALLY\n".plus(
            configuration.allDependencies.filter {
                // kotlin-bom does not need to be added to dependencies
                it.group != "org.jetbrains.kotlin" && it.name != "kotlin-bom"
            }.joinToString("\n") {
                if (isAvailableInAosp(it.group!!, it.name)) {
                    "${spaces(8)}\"${moduleNameAosp("${it.group}:${it.name}")}\","
                } else {
                    "${spaces(8)}\"${moduleName("${it.group}:${it.name}")}\","
                }
            }
        )

        // Replace existing dependencies with newly generated ones
        file.writeText(
            file.readText().replace(
                "static_libs: \\[.*?\\]".toRegex(RegexOption.DOT_MATCHES_ALL),
                "static_libs: [%s]".format("\n$dependencies\n${spaces(4)}")
            )
        )
    }

    // Update app/libs
    configuration.resolvedConfiguration.resolvedArtifacts.sortedBy {
        moduleString(it.moduleVersion.id)
    }.distinctBy {
        moduleString(it.moduleVersion.id)
    }.forEach {
        val id = it.moduleVersion.id

        // Skip modules that are available in AOSP
        if (isAvailableInAosp(id.group, it.name)) {
            return@forEach
        }

        // Get file path
        val dirPath = "${libsBase}/${modulePath(id)}"
        val filePath = "${dirPath}/${it.file.name}"

        // Copy artifact to app/libs
        it.file.copyTo(File(filePath))

        // Parse dependencies
        val dependencies =
            it.file.parentFile.parentFile.walk().filter { file -> file.extension == "pom" }
                .map { file ->
                    val ret = mutableListOf<String>()

                    val pom = XmlParser().parse(file)
                    val dependencies = (pom["dependencies"] as NodeList).firstOrNull() as Node?

                    dependencies?.children()?.forEach { node ->
                        val dependency = node as Node
                        ret.add(
                            "${
                                (dependency.get("groupId") as NodeList).text()
                            }:${
                                (dependency.get("artifactId") as NodeList).text()
                            }"
                        )
                    }

                    ret
                }.flatten()

        var targetSdkVersion = android.defaultConfig.targetSdk
        var minSdkVersion = 14

        // Extract AndroidManifest.xml for AARs
        if (it.file.extension == "aar") {
            copy {
                from(zipTree(filePath).matching { include("/AndroidManifest.xml") }.singleFile)
                into(dirPath)
            }

            val androidManifest = XmlParser().parse(File("${dirPath}/AndroidManifest.xml"))

            val usesSdk = (androidManifest["uses-sdk"] as NodeList).first() as Node
            targetSdkVersion = (usesSdk.get("@targetSdkVersion") as Int?) ?: targetSdkVersion
            minSdkVersion = (usesSdk.get("@minSdkVersion") as Int?) ?: minSdkVersion
        }

        // Write Android.bp
        File("$libsBase/Android.bp").let { file ->
            // Add autogenerated header if file is empty
            if (file.length() == 0L) {
                file.writeText("// DO NOT EDIT THIS FILE MANUALLY")
            }

            val formatDeps = { addNoDeps: Boolean ->
                val deps = dependencies.filter { dep ->
                    when {
                        configuration.resolvedConfiguration.resolvedArtifacts.firstOrNull { artifact ->
                            dep == "${artifact.moduleVersion.id.group}:${artifact.moduleVersion.id.name}"
                        } == null -> {
                            val moduleName = if (addNoDeps) {
                                moduleName(id)
                            } else {
                                "${moduleName(id)}-nodeps"
                            }
                            println("$moduleName: Skipping $dep because it's not in resolvedArtifacts")
                            false
                        }

                        dep == "org.jetbrains.kotlin:kotlin-stdlib-common" -> false
                        else -> true
                    }
                }.distinct().toMutableList()

                if (addNoDeps) {
                    // Add -nodeps dependency for android_library/java_library_static
                    deps.add(0, "${id.group}_${id.name}-nodeps")
                }

                var ret = ""

                if (deps.isNotEmpty()) {
                    deps.forEach { dep ->
                        ret += if (dep.contains(":")) {
                            val (group, artifactId) = dep.split(":")
                            if (isAvailableInAosp(group, artifactId)) {
                                "\n${spaces(8)}\"${moduleNameAosp(dep)}\","
                            } else {
                                "\n${spaces(8)}\"${moduleName(dep)}\","
                            }
                        } else {
                            "\n${spaces(8)}\"${moduleName(dep)}\","
                        }
                    }
                    ret += "\n${spaces(4)}"
                }

                ret
            }

            when (it.extension) {
                "aar" -> {
                    file.appendText(
                        """

                            android_library_import {
                                name: "${moduleName(id)}-nodeps",
                                aars: ["${modulePath(id)}/${it.file.name}"],
                                sdk_version: "$targetSdkVersion",
                                min_sdk_version: "$minSdkVersion",
                                apex_available: [
                                    "//apex_available:platform",
                                    "//apex_available:anyapex",
                                ],
                                static_libs: [%s],
                            }

                            android_library {
                                name: "${moduleName(id)}",
                                sdk_version: "$targetSdkVersion",
                                min_sdk_version: "$minSdkVersion",
                                apex_available: [
                                    "//apex_available:platform",
                                    "//apex_available:anyapex",
                                ],
                                manifest: "${modulePath(id)}/AndroidManifest.xml",
                                static_libs: [%s],
                                java_version: "1.7",
                            }

                        """.trimIndent().format(formatDeps(false), formatDeps(true))
                    )
                }

                "jar" -> {
                    file.appendText(
                        """

                            java_import {
                                name: "${moduleName(id)}-nodeps",
                                jars: ["${modulePath(id)}/${it.file.name}"],
                                sdk_version: "$targetSdkVersion",
                                min_sdk_version: "$minSdkVersion",
                                apex_available: [
                                    "//apex_available:platform",
                                    "//apex_available:anyapex",
                                ],
                            }

                            java_library_static {
                                name: "${moduleName(id)}",
                                sdk_version: "$targetSdkVersion",
                                min_sdk_version: "$minSdkVersion",
                                apex_available: [
                                    "//apex_available:platform",
                                    "//apex_available:anyapex",
                                ],
                                static_libs: [%s],
                                java_version: "1.7",
                            }

                        """.trimIndent().format(formatDeps(true))
                    )
                }

                else -> throw Exception("Unknown file extension: ${it.extension}")
            }
        }
    }
}
