// Copyright (C) 2022 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.agp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.plugin.parcelize)
  alias(libs.plugins.compose)
  alias(libs.plugins.mavenPublish)
  alias(libs.plugins.baselineprofile)
}

kotlin {
  // region KMP Targets
  androidTarget { publishLibraryVariants("release") }
  jvm()
  iosX64()
  iosArm64()
  iosSimulatorArm64()
  js(IR) {
    moduleName = property("POM_ARTIFACT_ID").toString()
    browser()
  }
  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    moduleName = property("POM_ARTIFACT_ID").toString()
    browser {
      // Necessary for tests
      testTask {
        useKarma {
          useChromeHeadless()
          useConfigDirectory(project.projectDir.resolve("karma.config.d").resolve("wasm"))
        }
      }
      binaries.executable()
    }
  }
  // endregion

  applyDefaultHierarchyTemplate()

  sourceSets {
    commonMain {
      dependencies {
        api(libs.compose.runtime)
        api(libs.compose.foundation)
        api(libs.kotlinx.immutable)
        api(libs.coroutines)
        api(projects.backstack)
        api(projects.circuitRuntime)
        api(projects.circuitRuntimePresenter)
        api(projects.circuitRuntimeUi)
        api(projects.circuitRetained)
        api(libs.compose.ui)
        implementation(libs.uuid)
      }
    }
    androidMain {
      dependencies {
        api(libs.androidx.compose.runtime)
        api(libs.androidx.compose.animation)
        implementation(libs.androidx.compose.integration.activity)
      }
    }
    commonTest {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(libs.molecule.runtime)
        implementation(libs.turbine)
        implementation(libs.coroutines.test)

        implementation(projects.internalTestUtils)
      }
    }
    val commonJvmTest =
      maybeCreate("commonJvmTest").apply {
        dependsOn(commonTest.get())
        dependencies {
          implementation(libs.kotlin.test)
          implementation(libs.compose.ui.testing.junit)
          implementation(libs.junit)
          implementation(libs.truth)
        }
      }
    jvmTest {
      dependsOn(commonJvmTest)
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation(libs.picnic)
      }
    }
    val androidUnitTest by getting {
      dependsOn(commonJvmTest)
      dependencies {
        implementation(libs.robolectric)
        implementation(libs.androidx.compose.foundation)
        implementation(libs.androidx.compose.ui.testing.junit)
        implementation(libs.androidx.compose.ui.testing.manifest)
      }
    }
    val androidInstrumentedTest by getting {
      dependencies {
        implementation(libs.junit)
        implementation(libs.coroutines.android)
        implementation(libs.androidx.compose.integration.activity)
        implementation(libs.compose.ui.testing.junit)
      }
    }
    // We use a common folder instead of a common source set because there is no commonizer
    // which exposes the browser APIs across these two targets.
    jsMain { kotlin.srcDir("src/browserMain/kotlin") }
    wasmJsMain { kotlin.srcDir("src/browserMain/kotlin") }
  }
}

tasks
  .withType<KotlinCompile>()
  .matching { it.name.contains("test", ignoreCase = true) }
  .configureEach {
    compilerOptions {
      freeCompilerArgs.addAll("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")

      if (name == "compileReleaseUnitTestKotlinAndroid") {
        freeCompilerArgs.addAll(
          "-P",
          "plugin:org.jetbrains.kotlin.parcelize:additionalAnnotation=com.slack.circuit.internal.test.Parcelize",
        )
      }
    }
  }

android {
  namespace = "com.slack.circuit.foundation"
  defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }

  testOptions { unitTests.isIncludeAndroidResources = true }
  testBuildType = "release"
}

baselineProfile {
  mergeIntoMain = true
  saveInSrc = true
  from(projects.samples.star.benchmark.dependencyProject)
  filter { include("com.slack.circuit.foundation.**") }
}
