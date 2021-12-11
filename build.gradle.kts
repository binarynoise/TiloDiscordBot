import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.0"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform(kotlin("bom")))
    
    // Use the Kotlin JDK 8 standard library.
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0-RC")
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.0-RC")
    
    implementation("com.jessecorbett:diskord-bot:2.1.2-SNAPSHOT")
    
    implementation(files("/usr/share/java/gtk-4.1.jar"))

}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("")
    archiveVersion.set("standalone")
    mergeServiceFiles()
    minimize()
    manifest {
        attributes(mapOf("Main-Class" to "MainKt"))
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}
