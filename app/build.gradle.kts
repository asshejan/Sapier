plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.sapier"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.sapier"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Specify supported ABIs
        ndk {
            abiFilters.add("x86_64")
            abiFilters.add("arm64-v8a")
        }
    }
    
    // Disable ABI splits to avoid R.jar issues
    splits {
        abi {
            isEnable = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/NOTICE.markdown"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
    
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    
    // Ensure we include native libraries for all ABIs
    packaging {
        resources {
            pickFirsts.add("**/*.so")
        }
    }
}

dependencies {
    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    

    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Image loading and processing
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    
    // ML Kit for text recognition and face detection
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:face-detection:16.1.5")
    
    // Network requests
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Simple HTTP client for API calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Google Photos API - Using simpler approach
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    // Removed problematic photoslibrary dependency - will use REST API directly
    implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")
    implementation("com.google.guava:guava:32.1.2-android")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // ViewModel and LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // DataStore for preferences
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Add a task to clean the build directory before building
tasks.register("forceClean") {
    doLast {
        println("Forcing clean of build directory")
        project.delete(fileTree("build") {
            include("**/R.jar")
        })
        // Try to release file handles by suggesting garbage collection
        System.gc()
    }
}

// Make sure the clean task depends on forceClean
tasks.named("clean") {
    dependsOn("forceClean")
}

// Add a property to skip resource processing if needed
val skipResourceProcessing = project.hasProperty("skipResourceProcessing") && 
    project.property("skipResourceProcessing") == "true"

// Disable resource processing tasks if skipResourceProcessing is true
if (skipResourceProcessing) {
    tasks.whenTaskAdded {
        if (name.contains("processResources") || name.contains("ProcessResources")) {
            enabled = false
            println("Disabled task: $name due to skipResourceProcessing flag")
        }
    }
}

// Add hooks to run forceClean before resource processing tasks
android.applicationVariants.all {
    val variantName = name.capitalize()
    tasks.named("process${variantName}Resources").configure {
        doFirst {
            println("Running forceClean before process${variantName}Resources")
            project.delete(fileTree("build") {
                include("**/R.jar")
            })
            // Try to release file handles by suggesting garbage collection
            System.gc()
            // Add a small delay to ensure file handles are released
            Thread.sleep(1000)
        }
    }
    
    // Also add a hook after the task to ensure R.jar is not locked
    tasks.named("process${variantName}Resources").configure {
        doLast {
            println("Running cleanup after process${variantName}Resources")
            // Try to release file handles by suggesting garbage collection
            System.gc()
        }
    }
}

// Add a gradle property to control ABI filters from command line
if (project.hasProperty("abiFilters")) {
    val abiFiltersValue = project.property("abiFilters").toString()
    val filters = abiFiltersValue.split(",")
    android.defaultConfig.ndk.abiFilters.clear()
    filters.forEach { filter ->
        android.defaultConfig.ndk.abiFilters.add(filter.trim())
    }
    println("Using ABI filters from command line: $abiFiltersValue")
}