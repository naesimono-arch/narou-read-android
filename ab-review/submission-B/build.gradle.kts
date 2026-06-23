import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.0"
    // JSON は Android 移植時の R8/ProGuard 耐性と Kotlin 親和性を重視し kotlinx.serialization を採用
    kotlin("plugin.serialization") version "2.1.0"
    application
}

group = "com.novelreader.pdfproto"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // PDFBox は Android 移植版(PDFBox-Android)が 2.x ベースのため 2.0 系で固定する
    implementation("org.apache.pdfbox:pdfbox:2.0.31")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.novelreader.pdfproto.MainKt")
}

// toolchain は自動DLを避け、Gradle 実行 JVM(JBR 21)をそのまま使う
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
