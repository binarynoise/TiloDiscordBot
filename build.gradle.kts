import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    kotlin("jvm") version "1.6.21"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

val javaVersion = JavaVersion.VERSION_1_8
val javaVersionNumber = javaVersion.name.substringAfter('_').replace('_', '.')
val javaVersionMajor = javaVersion.name.substringAfterLast('_')

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
        languageLevel = IdeaLanguageLevel(javaVersion)
        targetBytecodeVersion = javaVersion
    }
}

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform(kotlin("bom")))
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1")
    
    implementation("dev.kord:kord-core:0.8.0-M12")
    implementation("org.slf4j:slf4j-simple:1.7.36")
    
    implementation(files("/usr/share/java/gtk-4.1.jar"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = javaVersionNumber
}

tasks.withType<AbstractCompile> {
    sourceCompatibility = javaVersionNumber
    targetCompatibility = javaVersionNumber
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
        exclude(dependency("dev.kord:kord-core:.*"))
        exclude(dependency("org.slf4j:slf4j-simple:.*"))
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}
