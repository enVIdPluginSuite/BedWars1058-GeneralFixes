import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm") version "2.2.21" apply false
    id("com.gradleup.shadow") version "8.3.9" apply false
}

/** Every fix that exists. A plugin module lists the ones it ships in its gradle.properties (`fixes=a,b`). */
val allFixes = listOf("cobwebs", "chests", "swords", "tower", "shopnpcs")
// The BedWars1058 API is never fetched from a remote repository, it always comes from this local jar.
val bedWarsApiJar = layout.projectDirectory.file("libs/bedwars-api-24.9.jar").asFile

allprojects {
    group = "dev.etran"
    version = "1.2.0"

    repositories {
        mavenCentral()
        // Spigot API (compiled against 1.8.8 so the jars load on every version from 1.8 up to 26.x)
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    extensions.configure<KotlinJvmProjectExtension> {
        // Kotlin 2.2.21. Java 8 bytecode keeps the plugins loadable on 1.8 servers.
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
            freeCompilerArgs.addAll("-Xjvm-default=all")
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "8"
        targetCompatibility = "8"
    }

    dependencies {
        // 1.8.8 is the oldest supported version. Everything version specific is resolved at runtime.
        "compileOnly"("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT") {
            // Only needed for chat components, which these plugins do not use; the snapshot is not on any mirror.
            exclude(group = "net.md-5", module = "bungeecord-chat")
        }
        "compileOnly"(files(bedWarsApiJar))
    }

    // Everything except the shared library is a plugin that gets shaded into a jar.
    if (name != "fixes") {
        apply(plugin = "com.gradleup.shadow")

        dependencies {
            "implementation"(project(":fixes"))
        }

        tasks.named<ProcessResources>("processResources") {
            val props = mapOf("version" to project.version.toString())
            inputs.properties(props)
            filesMatching("plugin.yml") { expand(props) }
        }

        val shipped = (findProperty("fixes") as String? ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val jarName = findProperty("jarName") as String? ?: project.name

        tasks.named<ShadowJar>("shadowJar") {
            archiveClassifier.set("")
            archiveFileName.set("$jarName-${project.version}.jar")
            // The library holds all four fixes; each jar only keeps the ones it ships.
            for (fix in allFixes - shipped.toSet()) {
                exclude("dev/etran/generalfixes/$fix/**")
            }
            // Relocate the Kotlin runtime so it can never clash with another plugin shipping its own copy.
            relocate("kotlin", "dev.etran.generalfixes.libs.kotlin")
            relocate("org.jetbrains", "dev.etran.generalfixes.libs.jetbrains")
            relocate("org.intellij", "dev.etran.generalfixes.libs.intellij")
            exclude("META-INF/*.kotlin_module", "META-INF/maven/**", "META-INF/versions/**")
        }

        tasks.named("build") { dependsOn("shadowJar") }
        // The un-shaded jar is not usable on its own.
        tasks.named<Jar>("jar") { enabled = false }
    }
}

// ./gradlew allJars  ->  every plugin jar copied into build/jars
tasks.register<Copy>("allJars") {
    group = "build"
    description = "Builds all plugin jars into build/jars"
    from(subprojects.filter { it.name != "fixes" }.map { it.tasks.named("shadowJar") })
    into(layout.buildDirectory.dir("jars"))
}
