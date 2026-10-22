import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * إعداد القاعدة المشتركة (اختياري بالكامل).
 *
 * لا تُكتب أي مفاتيح داخل الشيفرة ولا داخل المستودع. تُقرأ القيم من:
 *  1) متغيّرات البيئة SHARED_DB_URL / SHARED_DB_KEY (تُستخدم في CI كأسرار)،
 *  2) وإلا من ملف محلي غير مرفوع اسمه sync.properties في جذر المشروع.
 *
 * إن غابت القيم تبقى القيمتان فارغتين، وتعمل الميزة كـ«غير مُفعّلة» بهدوء،
 * ويبقى التطبيق كاملاً يعمل بالتخزين المحلي ونفس تجربة الاستخدام.
 */
fun sharedDbValue(key: String): String {
    System.getenv(key)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
    val propsFile = rootProject.file("sync.properties")
    if (propsFile.exists()) {
        val props = Properties()
        propsFile.inputStream().use { props.load(it) }
        props.getProperty(key)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
    }
    return ""
}

val sharedDbUrl: String = sharedDbValue("SHARED_DB_URL")
val sharedDbKey: String = sharedDbValue("SHARED_DB_KEY")

android {
    namespace = "com.dalilyemen.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dalilyemen.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // التطبيق عربي أولاً مع دعم الإنجليزية
        resourceConfigurations += listOf("ar", "en")

        // القاعدة المشتركة: قيم تُحقن وقت البناء، وفارغة افتراضياً.
        buildConfigField("String", "SHARED_DB_URL", "\"$sharedDbUrl\"")
        buildConfigField("String", "SHARED_DB_KEY", "\"$sharedDbKey\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // نسخة أولية قابلة للتثبيت: تُوقَّع بمفتاح التطوير حتى تُضاف
            // مفاتيح التوقيع الإنتاجية عبر أسرار المستودع لاحقاً.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // سمة Material3 لملف الثيمات
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // تحميل الصور
    implementation("io.coil-kt:coil-compose:2.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // تنفيذ حقيقي لـ JSON داخل اختبارات الوحدة بدل النسخة الوهمية في أندرويد.
    // الاختبارات على الجهاز/المحاكي تستخدم نسخة النظام ولا تحتاج هذا السطر.
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
