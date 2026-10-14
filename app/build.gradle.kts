plugins{id("com.android.application");id("org.jetbrains.kotlin.android");id("org.jetbrains.kotlin.plugin.compose")}
android{
 namespace="com.ega.project.vibemusic";compileSdk=35
 defaultConfig{applicationId="com.ega.project.vibemusic";minSdk=30;targetSdk=35;versionCode=(project.findProperty("appVersionCode") as String?)?.toInt()?:1;versionName=(project.findProperty("appVersion") as String?)?:"1.0"}
 buildFeatures{compose=true}
 buildTypes{release{isMinifyEnabled=true;isShrinkResources=true
  proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"));signingConfig=signingConfigs.getByName("debug")}}
 compileOptions{sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17}
 kotlinOptions{jvmTarget="17"}
}
dependencies{
 implementation(platform("androidx.compose:compose-bom:2024.09.03"))
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.activity:activity-compose:1.9.2")
 implementation("androidx.media3:media3-exoplayer:1.4.1")
 implementation("androidx.media3:media3-session:1.4.1")
}
