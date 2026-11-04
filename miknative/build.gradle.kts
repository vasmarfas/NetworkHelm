import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndroid") {
                withJvm()
                withCompilations { it.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
            freeCompilerArgs.add("-Xjdk-release=17")
        }
    }
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "com.vasmarfas.networkhelm.miknative"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.coroutinesCore)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}

val labProperties = rootProject.layout.projectDirectory.file("lab.properties").asFile
tasks.named<Test>("jvmTest") {
    systemProperty("networkhelm.lab", labProperties.path)
    providers.gradleProperty("nativeIdle").orNull?.let { systemProperty("networkhelm.idle", it) }
    providers.gradleProperty("nativeSafeMode").orNull?.let { systemProperty("networkhelm.safemode", it) }
    providers.gradleProperty("nativeMeasure").orNull?.let { systemProperty("networkhelm.measure", it) }
    providers.gradleProperty("nativeRomon").orNull?.let { systemProperty("networkhelm.romon", it) }
    providers.gradleProperty("macTarget").orNull?.let { systemProperty("networkhelm.macTarget", it) }
    providers.gradleProperty("macFrom").orNull?.let { systemProperty("networkhelm.macFrom", it) }
    if (labProperties.exists()) doNotTrackState("lab tests depend on live routers")
}
