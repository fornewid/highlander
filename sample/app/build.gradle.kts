plugins {
    alias(libs.plugins.android.application)
    id("io.github.fornewid.highlander")
}

android {
    namespace = "io.github.fornewid.highlander.sample.app"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        targetSdk = 36
    }
}

dependencies {
    implementation(project(":sample:module1"))
    implementation(libs.androidx.activity)
}

highlander {
    configuration("release") {
        sources = true
    }
}
