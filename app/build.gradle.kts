plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.ailecturesummarizer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ailecturesummarizer"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Thumbnail loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Grid Layout and YouTube Player from Noa AI
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0")

    // DrawerLayout + RecyclerView (explicit to avoid transitive-only dependency)
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Gson (explicitly declared, also included via converter-gson)
    implementation("com.google.code.gson:gson:2.10.1")

    // Supabase auth: OkHttp core HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Google Sign-In (for Google OAuth token exchange with Supabase)
    implementation("com.google.android.gms:play-services-auth:21.2.0")
}
