import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "8.3.9"
}

group = "dev.etran"
version = "1.0.0"

repositories {
    mavenCentral()
    // Spigot API (compiled against 1.8.8 so the jar loads on every version from 1.8 up to 26.x)
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    // 1.8.8 is the oldest supported version. Everything version specific is resolved at runtime,
    // so one jar covers 1.8 - 26.3.
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT") {
        // Only needed for chat components, which this plugin does not use; the snapshot is not on any mirror.
        exclude(group = "net.md-5", module = "bungeecord-chat")
    }

    // The BedWars1058 API is never fetched from a remote repository, it always comes from this local jar.
    compileOnly(files("libs/bedwars-api-24.9.jar"))

    implementation(kotlin("stdlib"))
}

kotlin {
    // Kotlin 2.2.21. Java 8 bytecode keeps the plugin loadable on 1.8 servers.
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.addAll("-Xjvm-default=all")
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "8"
    targetCompatibility = "8"
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("${project.name}-${project.version}.jar")
    // Relocate the Kotlin runtime so it can never clash with another plugin shipping its own copy.
    relocate("kotlin", "dev.etran.generalfixes.libs.kotlin")
    relocate("org.jetbrains", "dev.etran.generalfixes.libs.jetbrains")
    relocate("org.intellij", "dev.etran.generalfixes.libs.intellij")
    exclude("META-INF/*.kotlin_module", "META-INF/maven/**", "META-INF/versions/**")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    // The un-shaded jar is not usable on its own.
    enabled = false
}

// ./gradlew allJars  ->  build/jars (only one jar in this version)
tasks.register<Copy>("allJars") {
    group = "build"
    description = "Builds the plugin jar into build/jars"
    from(tasks.shadowJar)
    into(layout.buildDirectory.dir("jars"))
}
