plugins {
    kotlin("jvm") version "2.3.20"
    id("com.gradleup.shadow") version "9.4.1"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }
}

group = "org.stephanosbad.integrare.demo"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc-repo" }
    maven("https://repo.oraxen.com/releases") { name = "oraxen-repo" }
    maven("https://repo.nexomc.com/releases") { name = "nexo-repo" }
    maven("https://repo.codemc.io/repository/maven-public/") { name = "codemc-repo" }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paperVersion")}")
    compileOnly("beer.devs:itemsadder-api:${property("itemsAdderVersion")}")
    compileOnly("io.th0rgal:oraxen:${property("oraxenVersion")}")
    compileOnly("com.nexomc:nexo:${property("nexoVersion")}") {
        exclude(group = "com.google.code.gson", module = "gson")
    }

    // The library this plugin exists to exercise
    implementation(project(":"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${property("kotlinVersion")}")
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveBaseName.set("IntegrareDemo")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())

    // Relocate Kotlin and Item-NBT-API (brought in transitively via Integrare) to
    // avoid colliding with other plugins that also bundle them - same relocation
    // CharmedChars uses as an Integrare consumer.
    relocate("kotlin", "org.stephanosbad.integrare.demo.kotlin")
    relocate("de.tr7zw.changeme.nbtapi", "org.stephanosbad.integrare.demo.nbtapi")

    destinationDirectory.set(layout.buildDirectory.dir("libs"))
}

tasks.build { dependsOn(tasks.shadowJar) }
tasks.runServer { minecraftVersion("26.3") }
