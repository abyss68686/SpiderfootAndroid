plugins {
    id("com.android.application")
    id("com.chaquo.python")
}

val pythonCommand = providers.gradleProperty("pythonCommand")
    .orElse("python3.10")
    .get()

android {
    namespace = "de.steve.spiderfoot"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.steve.spiderfoot"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-beta.1"

        ndk {
            // Steve's SM-G965F and practically all current Android devices.
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        debug {
            // Preview certificate, but no debuggable installed process.
            isDebuggable = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.10"
        buildPython(pythonCommand)

        pip {
            install(
                "-c", "src/main/python/constraints-android.txt",
                "-r", "src/main/python/requirements-android.txt"
            )
        }

        // SpiderFoot discovers modules by listing their .py files at runtime.
        pyc {
            src = false
        }

        // SpiderFoot also reads templates, static assets, YAML and wordlists by path.
        extractPackages("sfapp")
    }
}
