import java.util.Properties

plugins {
    kotlin("jvm") version "2.0.21"
    application
}

val versionFile = rootProject.file("../version.properties")
val appVersion = Properties().apply {
    versionFile.inputStream().use { load(it) }
}.getProperty("versionName", "0.0.0").trim()

version = appVersion

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.kwhat:jnativehook:2.2.2")
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.maoyou.typingpet.desktop.MainKt")
}

// アニメーション GIF は Android 側の素材を共有する
val rawAnimations = rootProject.fileTree("../app/src/main/res/raw") {
    include("*.gif")
}

tasks.processResources {
    from(rawAnimations) {
        into("animations")
    }
}

// 通常の jar と fatJar が同じファイル名にならないようにする
tasks.jar {
    archiveClassifier.set("plain")
}

tasks.register<Jar>("fatJar") {
    group = "build"
    description = "依存ライブラリを含む実行可能な jar を作る"
    archiveBaseName.set("catsquid-desktop")
    archiveVersion.set(appVersion)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.maoyou.typingpet.desktop.MainKt"
    }
    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
}
