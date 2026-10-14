plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}

// Builds fora do OneDrive: a sincronização trava arquivos em app/build e quebra a compilação.
val externalBuildRoot = System.getenv("LOCALAPPDATA")?.let { file("$it/DoHomeControlBuild") }
if (externalBuildRoot != null) {
    allprojects {
        layout.buildDirectory.set(externalBuildRoot.resolve(if (path == ":") "root" else name))
    }
}
