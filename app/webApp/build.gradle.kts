import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    js {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":app:shared"))

            implementation(libs.compose.ui)
        }
    }
}

val bundleExcludes = arrayOf("*.map", "composeResources/com.composables.composeunstyled_platformtheme.generated.resources/**")

// both builds side by side: WebAssembly where the browser has WebAssembly GC, JavaScript where it has not
tasks.register<Sync>("webAppBundle") {
    from(tasks.named("composeCompatibilityBrowserDistribution"))
    exclude(*bundleExcludes)
    into(layout.buildDirectory.dir("bundle"))
}

// the router keeps the page in its flash, so its package takes the WebAssembly build alone and leaves out 21 MB of the JavaScript one
val webAppWasmBundle = tasks.register<Sync>("webAppWasmBundle") {
    from(tasks.named("wasmJsBrowserDistribution"))
    exclude(*bundleExcludes)
    into(layout.buildDirectory.dir("bundle-wasm"))
}

tasks.register<Tar>("openWrtPackage") {
    from(webAppWasmBundle) { into("www/networkhelm") }
    from("openwrt/networkhelm.json") { into("usr/share/rpcd/acl.d") }
    compression = Compression.GZIP
    archiveFileName.set("networkhelm-openwrt-${rootProject.extra["appVersionName"]}.tar.gz")
    destinationDirectory.set(layout.buildDirectory.dir("openwrt"))
}

tasks.register<Sync>("pagesBundle") {
    val version = rootProject.extra["appVersionName"].toString()
    val head = """    <meta name="networkhelm-mode" content="standalone">
    <meta name="theme-color" content="#14202b">
    <link rel="manifest" href="manifest.webmanifest">
    <link rel="apple-touch-icon" href="pwa-192.png">
"""
    val tail = """    <script type="application/javascript">
        if ('serviceWorker' in navigator) window.addEventListener('load', () => navigator.serviceWorker.register('sw.js'));
    </script>
"""
    from(tasks.named("webAppBundle"))
    from("pages")
    filesMatching("index.html") {
        filter { line ->
            when (line.trim()) {
                "</head>" -> head + line
                "</body>" -> tail + line
                else -> line
            }
        }
    }
    filesMatching("sw.js") { filter { it.replace("@VERSION@", version) } }
    into(layout.buildDirectory.dir("pages"))
}
