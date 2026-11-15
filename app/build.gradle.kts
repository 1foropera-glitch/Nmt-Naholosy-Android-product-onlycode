import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import org.gradle.api.tasks.testing.Test
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import groovy.json.JsonSlurper
import java.io.File
import java.net.URI
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
}

abstract class VerifyReleaseConfiguration : DefaultTask() {
  @get:Input abstract val admobAppId: Property<String>
  @get:Input abstract val admobUnitId: Property<String>
  @get:Input abstract val productionFirebase: Property<String>
  @get:Input abstract val developmentFirebase: Property<String>
  @get:Input abstract val privacyUrl: Property<String>
  @get:Input abstract val deletionUrl: Property<String>
  @get:Input abstract val supportEmail: Property<String>
  @get:Input abstract val playPublicKey: Property<String>
  @get:Input abstract val signingConfigured: Property<Boolean>

  @TaskAction fun verify() {
    val problems = mutableListOf<String>()
    fun requireValue(ok: Boolean, message: String) { if (!ok) problems.add(message) }
    fun publicUrl(value: String): Boolean = runCatching {
      val uri = URI(value)
      uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
        uri.host !in listOf("localhost", "example.com", "example.org") && !uri.host.endsWith(".example")
    }.getOrDefault(false)
    requireValue(admobAppId.get().matches(Regex("ca-app-pub-[0-9]+~[0-9]+")) && !admobAppId.get().contains("3940256099942544"), "Configure production ADMOB_APP_ID.")
    requireValue(admobUnitId.get().matches(Regex("ca-app-pub-[0-9]+/[0-9]+")) && !admobUnitId.get().contains("3940256099942544"), "Configure production ADMOB_INTERSTITIAL_ID.")
    fun config(value: String): Map<*, *>? = runCatching { JsonSlurper().parseText(value) as Map<*, *> }.getOrNull()
    val prod = config(productionFirebase.get())
    val dev = config(developmentFirebase.get())
    val prodProject = (prod?.get("project_info") as? Map<*, *>)?.get("project_id") as? String
    val devProject = (dev?.get("project_info") as? Map<*, *>)?.get("project_id") as? String
    requireValue(!prodProject.isNullOrBlank() && !prodProject.startsWith("demo-"), "Add valid app/src/release/google-services.json from the production Firebase project.")
    requireValue(prodProject == null || prodProject != devProject, "Production and development Firebase projects must be different.")
    val clients = prod?.get("client") as? List<*> ?: emptyList<Any>()
    val client = clients.filterIsInstance<Map<*, *>>().firstOrNull {
      ((it["client_info"] as? Map<*, *>)?.get("android_client_info") as? Map<*, *>)?.get("package_name") == "com.intervalninaholosy.nmt"
    }
    requireValue(client != null, "Production Firebase config must contain com.intervalninaholosy.nmt.")
    requireValue((client?.get("oauth_client") as? List<*>)?.filterIsInstance<Map<*, *>>()?.any { (it["client_type"] as? Number)?.toInt() == 3 } == true, "Production Firebase config must contain a Web OAuth client (type 3).")
    requireValue(publicUrl(privacyUrl.get()), "Set PRIVACY_POLICY_URL to your public HTTPS policy page.")
    requireValue(publicUrl(deletionUrl.get()), "Set ACCOUNT_DELETION_URL to your public HTTPS deletion request page.")
    requireValue(supportEmail.get().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) && !supportEmail.get().endsWith("@example.com"), "Set SUPPORT_EMAIL to your support address.")
    requireValue(runCatching {
      val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(
        Base64.getDecoder().decode(playPublicKey.get()))) as RSAPublicKey
      key.modulus.bitLength() >= 2048
    }.getOrDefault(false), "Add the Google Play public licensing RSA key to play-licensing-public-key.txt (Base64).")
    requireValue(signingConfigured.get(), "Set KEYSTORE_PATH to an existing upload key and provide STORE_PASSWORD / KEY_PASSWORD locally.")
    check(problems.isEmpty()) { "Release configuration is incomplete:\n" + problems.joinToString("\n") { " - $it" } }
  }
}

fun buildConfigString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

val localProperties = Properties().apply {
  val file = rootProject.file("local.properties")
  if (file.isFile) {
    file.reader(Charsets.UTF_8).use { load(it) }
  }
}

fun findSigningProperty(key: String): String? {
  return System.getenv(key)?.takeIf { it.isNotBlank() }
    ?: (project.findProperty(key) as? String)?.takeIf { it.isNotBlank() }
    ?: localProperties.getProperty(key)?.takeIf { it.isNotBlank() }
}

fun resolveKeystoreFile(rawPath: String?): File? {
  if (rawPath.isNullOrBlank()) return null
  val cleanPath = if (rawPath.startsWith("/") && rawPath.length > 3 && rawPath[2] == ':') {
    rawPath.substring(1)
  } else {
    rawPath
  }
  val candidate = File(cleanPath)
  return when {
    candidate.isAbsolute && candidate.exists() -> candidate
    rootProject.file(cleanPath).exists() -> rootProject.file(cleanPath)
    project.file(cleanPath).exists() -> project.file(cleanPath)
    else -> candidate
  }
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.intervalninaholosy.nmt"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "1.0.1"
    for (key in listOf("PRIVACY_POLICY_URL", "ACCOUNT_DELETION_URL", "SUPPORT_EMAIL")) {
      buildConfigField("String", key, buildConfigString(providers.gradleProperty(key).orElse(if (key == "SUPPORT_EMAIL") "wrocki.devoloper@gmail.com" else "").get()))
    }

    buildConfigField("String", "PLAY_LICENSING_PUBLIC_KEY", buildConfigString(
      providers.fileContents(rootProject.layout.projectDirectory.file("play-licensing-public-key.txt")).asText.orElse("").get().replace(Regex("\\s"), "")))
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
  }

  signingConfigs {
    create("release") {
      val rawKeystorePath = findSigningProperty("KEYSTORE_PATH")
      val keystoreFile = resolveKeystoreFile(rawKeystorePath) ?: file("${rootDir}/my-upload-key.jks")
      storeFile = keystoreFile
      storePassword = findSigningProperty("STORE_PASSWORD")
      keyAlias = findSigningProperty("KEY_ALIAS") ?: "upload"
      keyPassword = findSigningProperty("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      manifestPlaceholders["admobAppId"] = providers.gradleProperty("ADMOB_APP_ID").orElse("").get()
      val adUnit = providers.gradleProperty("ADMOB_INTERSTITIAL_ID").orElse("").get()
      require(adUnit.isEmpty() || adUnit.matches(Regex("ca-app-pub-[0-9]+/[0-9]+")))
      buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$adUnit\"")
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

googleServices {
  missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}


// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.work.runtime.ktx)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.firebase.analytics)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.appcheck.playintegrity)
  implementation(libs.firebase.crashlytics)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services.auth)
  implementation(libs.googleid)
  implementation(libs.konfetti.compose)
  implementation(libs.play.services.ads)
  implementation(libs.user.messaging.platform)
  implementation(libs.billing.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.moshi.kotlin)
  // implementation(libs.play.services.location)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation("com.google.firebase:firebase-appcheck-debug")
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Keep source/test text UTF-8 independently of the daemon's native argsfile encoding.
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.withType<Test>().configureEach {
  systemProperty("file.encoding", "UTF-8")
}

val verifyReleaseConfiguration by tasks.registering(VerifyReleaseConfiguration::class) {
  admobAppId.set(providers.gradleProperty("ADMOB_APP_ID").orElse(""))
  admobUnitId.set(providers.gradleProperty("ADMOB_INTERSTITIAL_ID").orElse(""))
  productionFirebase.set(providers.fileContents(layout.projectDirectory.file("src/release/google-services.json")).asText.orElse(""))
  developmentFirebase.set(providers.fileContents(layout.projectDirectory.file("src/debug/google-services.json")).asText
    .orElse(providers.fileContents(layout.projectDirectory.file("google-services.json")).asText).orElse(""))
  privacyUrl.set(providers.gradleProperty("PRIVACY_POLICY_URL").orElse(""))
  deletionUrl.set(providers.gradleProperty("ACCOUNT_DELETION_URL").orElse(""))
  supportEmail.set(providers.gradleProperty("SUPPORT_EMAIL").orElse("wrocki.devoloper@gmail.com"))
  playPublicKey.set(providers.fileContents(rootProject.layout.projectDirectory.file("play-licensing-public-key.txt")).asText.orElse("").map { it.replace(Regex("\\s"), "") })
  val releaseKey = resolveKeystoreFile(findSigningProperty("KEYSTORE_PATH"))
  signingConfigured.set(
    releaseKey != null &&
    releaseKey.isFile &&
    !findSigningProperty("STORE_PASSWORD").isNullOrBlank() &&
    !findSigningProperty("KEY_PASSWORD").isNullOrBlank()
  )
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseConfiguration) }
