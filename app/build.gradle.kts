import java.io.File
import java.io.FileInputStream
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

android {
    signingConfigs {
        create("release") {
            storeFile = file(localProperties.getProperty("storeFile", ""))
            storePassword = localProperties.getProperty("storePassword", "")
            keyAlias = localProperties.getProperty("keyAlias", "")
            keyPassword = localProperties.getProperty("keyPassword", "")
        }
    }

    namespace = "com.hdrd13.luminancewall"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "com.hdrd13.luminancewall"
        minSdk = 33
        targetSdk = 37
        versionCode = 2
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.glasmio.scaffold)
}

base {
    archivesName.set("LuminanceWall-v2.0")
}

abstract class GenerateSymbolFont : DefaultTask() {
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceFont: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()
        sourceFont.orNull?.asFile?.let { font ->
            val sig = font.inputStream().use { it.readNBytes(4).toList() }
            require(
                sig == listOf<Byte>(0, 1, 0, 0) ||
                    sig == "OTTO".toByteArray().toList() ||
                    sig == "true".toByteArray().toList()
            ) { "${font.name} is not an OpenType or TrueType font." }
            font.copyTo(output.resolve("glasmio_symbols.ttf"), overwrite = true)
        }
    }
}

val symbolFontPath: String? = (providers.gradleProperty("glasmio.symbolFont").orNull
    ?: localProperties.getProperty("glasmio.symbolFont"))
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

val generateSymbolFont = tasks.register<GenerateSymbolFont>("generateSymbolFont") {
    symbolFontPath?.let { sourceFont.set(layout.file(provider { File(it) })) }
    outputDirectory.set(layout.buildDirectory.dir("generated/symbol-font/assets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            generateSymbolFont,
            GenerateSymbolFont::outputDirectory,
        )
    }
}