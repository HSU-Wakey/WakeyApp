plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.wakey"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.wakey"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["android:largeHeap"] = true
    }

    // buildTypes 중복 제거
    buildTypes {
        debug {
            buildConfigField("String", "GOOGLE_VISION_API_KEY", "\"2eebeeb849390e974963949d05f2b71577794531\"")
            buildConfigField("String", "GEMINI_API_KEY", "\"여기에_Gemini_API_키\"")  // 추가
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "GOOGLE_VISION_API_KEY", "\"2eebeeb849390e974963949d05f2b71577794531\"")
            buildConfigField("String", "GEMINI_API_KEY", "\"여기에_Gemini_API_키\"")  // 추가
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    aaptOptions {
        noCompress += "tflite"
    }

    buildFeatures {
        mlModelBinding = true
        buildConfig = true
    }
}

configurations.all {
    resolutionStrategy {
        // 충돌을 일으키는 라이브러리 강제 버전 지정
        force("androidx.databinding:databinding-common:8.9.1")
    }

    // exclude 구문 수정
    exclude(group = "androidx.databinding", module = "baseLibrary")
    exclude(group = "com.android.support")
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.activity:activity:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.gms:play-services-maps:19.1.0")
    implementation("com.google.android.libraries.places:places:4.1.0")
    implementation("androidx.exifinterface:exifinterface:1.4.0")
    implementation("com.github.chrisbanes:PhotoView:2.3.0") {
        exclude(group = "com.android.support")
    }

    // TensorFlow Lite + Flex Delegate
    implementation("org.tensorflow:tensorflow-lite:2.12.0")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.12.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.2")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.1.0-rc2")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-common:2.6.1")
    implementation(libs.vision.common)
    implementation(libs.compiler)
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Maps Utils, Glide, Gson
    implementation("com.google.maps.android:android-maps-utils:2.3.0") {
        exclude(group = "com.android.support")
    }
    implementation("com.github.bumptech.glide:glide:4.15.1")
    annotationProcessor("com.github.bumptech.glide:compiler:4.15.1")
    implementation("com.google.code.gson:gson:2.9.0")

    //upscaler
    implementation("org.tensorflow:tensorflow-lite:2.13.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.3")

    // PhotoView 중복 제거 (위에서 이미 선언됨)
//    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")

    // Google Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // OkHttp (JSON HTTP 요청용 - API 호출에 필요)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // MaterialCalendarView 라이브러리
    implementation("com.github.prolificinteractive:material-calendarview:2.0.0") {
        exclude(group = "com.android.support")
    }

    implementation("com.jakewharton.threetenabp:threetenabp:1.4.0")

    // Google Cloud Vision API 관련 의존성 제거 (REST API만 사용하므로 불필요)
    // implementation("com.google.cloud:google-cloud-vision:3.0.0")
    // implementation("com.google.auth:google-auth-library-oauth2-http:1.3.0")
    // implementation("io.grpc:grpc-okhttp:1.54.1")
}