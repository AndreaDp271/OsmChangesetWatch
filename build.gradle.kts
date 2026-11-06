plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
}

// Formattazione unica per tutto il codice: ./gradlew spotlessApply (spotlessCheck la verifica)
spotless {
    val ktfmt = libs.versions.ktfmt.get()
    kotlin {
        target("app/src/**/*.kt")
        ktfmt(ktfmt).kotlinlangStyle()
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktfmt(ktfmt).kotlinlangStyle()
    }
}
