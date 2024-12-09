import com.powersync.plugins.sonatype.setupGithubRepository
import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublishPlugin)
    alias(libs.plugins.downloadPlugin)
    alias(libs.plugins.kotlinter)
    id("com.powersync.plugins.sonatype")
    alias(libs.plugins.mokkery)
}

val sqliteVersion = "3450200"
val sqliteReleaseYear = "2024"

val sqliteSrcFolder =
    project.layout.buildDirectory
        .dir("interop/sqlite")
        .get()

val downloadSQLiteSources by tasks.registering(Download::class) {
    val zipFileName = "sqlite-amalgamation-$sqliteVersion.zip"
    val destination = sqliteSrcFolder.file(zipFileName).asFile
    src("https://www.sqlite.org/$sqliteReleaseYear/$zipFileName")
    dest(destination)
    onlyIfNewer(true)
    overwrite(false)
}

val unzipSQLiteSources by tasks.registering(Copy::class) {
    dependsOn(downloadSQLiteSources)

    from(
        zipTree(downloadSQLiteSources.get().dest).matching {
            include("*/sqlite3.*")
            exclude {
                it.isDirectory
            }
            eachFile {
                this.path = this.name
            }
        },
    )
    into(sqliteSrcFolder)
}

val buildCInteropDef by tasks.registering {
    dependsOn(unzipSQLiteSources)

    val cFile = sqliteSrcFolder.file("sqlite3.c").asFile
    val defFile = sqliteSrcFolder.file("sqlite3.def").asFile

    doFirst {
        defFile.writeText(
            """
            package = com.powersync.sqlite3
            ---

            """.trimIndent() + cFile.readText(),
        )
    }
    outputs.files(defFile)
}

kotlin {
    androidTarget {
        publishLibraryVariants("release", "debug")
    }
    jvm()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    targets.withType<KotlinNativeTarget> {
        compilations.named("main") {
            compileTaskProvider {
                compilerOptions.freeCompilerArgs.add("-Xexport-kdoc")
            }
            cinterops.create("sqlite") {
                val cInteropTask = tasks[interopProcessingTaskName]
                cInteropTask.dependsOn(buildCInteropDef)
                definitionFile =
                    buildCInteropDef
                        .get()
                        .outputs.files.singleFile
                compilerOpts.addAll(listOf("-DHAVE_GETHOSTUUID=0"))
            }
            cinterops.create("powersync-sqlite-core")
        }
    }

    explicitApi()

    sourceSets {
        all {
            languageSettings {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }

        commonMain.dependencies {
            implementation(libs.uuid)
            implementation(libs.kotlin.stdlib)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentnegotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.io)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.stately.concurrency)
            implementation(libs.configuration.annotations)
            api(project(":persistence"))
            api(libs.kermit)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqlite.jdbc)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.ios)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.test.coroutines)
            implementation(libs.kermit.test)
        }
    }
}

android {
    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            buildConfigField("boolean", "DEBUG", "false")
        }
        debug {
            buildConfigField("boolean", "DEBUG", "true")
        }
    }

    namespace = "com.powersync"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                arguments.addAll(
                    listOf(
                        "-DSQLITE3_SRC_DIR=${sqliteSrcFolder.asFile.absolutePath}",
                    ),
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = project.file("src/androidMain/cpp/CMakeLists.txt")
        }
    }
}

val cmakeJvmConfigure = tasks.register<Exec>("cmakeJvmConfigure") {
    dependsOn(unzipSQLiteSources)
    workingDir = layout.buildDirectory.dir("cmake").get().asFile
    inputs.files(
        "src/jvmMain/cpp",
        "src/jvmNative/cpp",
        sqliteSrcFolder,
    )
    outputs.dir(workingDir)
    executable = "cmake"
    args(file("src/jvmMain/cpp/CMakeLists.txt").absolutePath)
    doFirst {
        workingDir.mkdirs()
    }
}

val cmakeJvmBuild = tasks.register<Exec>("cmakeJvmBuild") {
    dependsOn(cmakeJvmConfigure)
    workingDir = layout.buildDirectory.dir("cmake").get().asFile
    inputs.files(
        "src/jvmMain/cpp",
        "src/jvmNative/cpp",
        sqliteSrcFolder,
        workingDir,
    )
    outputs.dir(workingDir.resolve("output"))
    executable = "cmake"
    args("--build", ".")
}

val binariesFolder =
    project.layout.buildDirectory
        .dir("binaries/desktop")
        .get()

val downloadDesktopBinaries = tasks.register<Download>("downloadDesktopBinaries") {
    val coreVersion = libs.versions.powersync.core.get()
    src(listOf(
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/libpowersync_aarch64.so",
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/libpowersync_x64.so",
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/libpowersync_aarch64.dylib",
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/libpowersync_x64.dylib",
        "https://github.com/powersync-ja/powersync-sqlite-core/releases/download/v$coreVersion/powersync_x64.dll",
    ))
    dest(binariesFolder)
    onlyIfModified(true)
}

tasks.named<ProcessResources>(kotlin.jvm().compilations["main"].processResourcesTaskName) {
    dependsOn(cmakeJvmBuild, downloadDesktopBinaries)
    from(layout.buildDirectory.dir("cmake/output"))
    from(binariesFolder)
}

afterEvaluate {
    val buildTasks =
        tasks.matching {
            val taskName = it.name
            if (taskName.contains("Clean")) {
                return@matching false
            }
            if (taskName.contains("externalNative") || taskName.contains("CMake") || taskName.contains("generateJsonModel")) {
                return@matching true
            }
            return@matching false
        }

    buildTasks.forEach {
        it.dependsOn(buildCInteropDef)
    }
}

setupGithubRepository()
