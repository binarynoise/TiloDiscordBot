import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    kotlin("jvm") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

val javaVersion = JavaVersion.VERSION_17

idea {
    module {
        languageLevel = IdeaLanguageLevel(javaVersion)
        targetBytecodeVersion = javaVersion
    }
}

repositories {
    mavenCentral()
    //maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    // Align versions of all Kotlin components
    implementation("org.jetbrains.kotlinx:atomicfu:0.23.1")
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    val kordVersion = "0.12.0"
    implementation("dev.kord:kord-gateway:$kordVersion")
    implementation("dev.kord:kord-rest:$kordVersion")
    
    implementation("org.slf4j:slf4j-simple:2.0.10")
    
    implementation(files("/usr/share/java/gtk-4.1.jar"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = javaVersion.toString()
}

tasks.withType<JavaCompile> {
    sourceCompatibility = javaVersion.toString()
    targetCompatibility = javaVersion.toString()
}

tasks.withType<Jar> {
    manifest {
        attributes(mapOf("Main-Class" to "MainKt"))
    }
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("shadow")
    mergeServiceFiles()
    minimize {
        exclude(dependency("dev.kord:kord-gateway:.*"))
        exclude(dependency("org.slf4j:slf4j-simple:.*"))
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}
