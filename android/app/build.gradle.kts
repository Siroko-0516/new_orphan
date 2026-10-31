import java.util.Properties
import java.security.MessageDigest

plugins { id("com.android.application") }

val bundledModelDir = providers.gradleProperty("bundledModelDir")
android {
    namespace = "dev.giljabi"
    compileSdk = 35
    ndkVersion = "27.0.12077973"
    defaultConfig {
        applicationId = "dev.giljabi.camera"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = if (bundledModelDir.isPresent) "0.4.0-full" else "0.4.0-lite"
        buildConfigField("boolean", "BUNDLED_AI", bundledModelDir.isPresent.toString())
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_shared", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"); targets += "giljabi_native" } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { jniLibs.useLegacyPackaging = true }
    buildTypes { release { isMinifyEnabled = false } }
    buildFeatures { buildConfig = true }
    androidResources { noCompress += "gguf" }
}
dependencies {
    implementation("androidx.core:core:1.15.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
val prepareYolo by tasks.registering(Copy::class) {
    from(rootProject.file("../public/yolo11n.onnx"))
    into(layout.buildDirectory.dir("generated/yolo-assets"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/yolo-assets"))
tasks.named("preBuild") { dependsOn(prepareYolo) }

if (bundledModelDir.isPresent) {
    val pack = file(bundledModelDir.get())
    val prepareBundledModels by tasks.registering(Sync::class) {
        from(pack)
        into(layout.buildDirectory.dir("generated/bundled-assets/local-ai"))
        doFirst {
            listOf("manifest.properties", "model.gguf", "mmproj.gguf", "LICENSE.txt", "provenance.json").forEach {
                check(file("${pack.absolutePath}/$it").isFile) { "Missing bundled AI file: $it. Run scripts/bundle_models.py first." }
            }
            val metadata = Properties().apply { file("${pack.absolutePath}/manifest.properties").inputStream().use { load(it) } }
            check(metadata.getProperty("format") == "1") { "Unsupported AI pack" }
            listOf("model" to "model.gguf", "vision" to "mmproj.gguf").forEach { (key, name) ->
                val weight = file("${pack.absolutePath}/$name")
                check(weight.length() == metadata.getProperty("$key.bytes").toLong()) { "AI pack size mismatch: $name" }
                val sha = MessageDigest.getInstance("SHA-256")
                weight.inputStream().use { input -> val block = ByteArray(1024*1024); var n = input.read(block); while(n != -1) { sha.update(block,0,n); n = input.read(block) } }
                check(sha.digest().joinToString("") { "%02x".format(it) } == metadata.getProperty("$key.sha256")) { "AI pack checksum mismatch: $name" }
            }
        }
    }
    android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/bundled-assets"))
    tasks.named("preBuild") { dependsOn(prepareBundledModels) }
}
