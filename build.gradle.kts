import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.0"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform(kotlin("bom")))
    
    // Use the Kotlin JDK 8 standard library.
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.0")
    
    implementation("dev.kord:kord-core:0.8.x-SNAPSHOT")
    implementation("org.slf4j:slf4j-simple:1.7.33")
    
    implementation(files("/usr/share/java/gtk-4.1.jar"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("")
    archiveVersion.set("standalone")
    mergeServiceFiles()
//    minimize()
    manifest {
        attributes(mapOf("Main-Class" to "MainKt"))
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}
