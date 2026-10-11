
plugins {
    kotlin("jvm") version "2.3.20"
    `java-library`
    `maven-publish`
    jacoco
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }
}

jacoco {
    toolVersion = "0.8.15"
}

group = property("group")!!
version = property("version")!!

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc-repo" }
    maven("https://jitpack.io") { name = "jitpack" }
    maven("https://repo.oraxen.com/releases") { name = "oraxen-repo" }
    maven("https://repo.nexomc.com/releases") { name = "nexo-repo" }
    maven("https://repo.codemc.io/repository/maven-public/") { name = "codemc-repo" }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${property("paperVersion")}")
    compileOnly("beer.devs:itemsadder-api:${property("itemsAdderVersion")}")
    compileOnly("io.th0rgal:oraxen:${property("oraxenVersion")}")
    compileOnly("com.nexomc:nexo:${property("nexoVersion")}") { // Note: Untested - no premium license available for verification
        exclude(group = "com.google.code.gson", module = "gson")
    }

    // Exposed to consumers, who must shade and relocate it (see README)
    api("de.tr7zw:item-nbt-api:${property("nbtApiVersion")}")

    // Unit testing framework
    testImplementation("io.papermc.paper:paper-api:${property("paperVersion")}")
    testImplementation("org.jetbrains.kotlin:kotlin-test:${property("kotlinVersion")}")
    testImplementation("io.mockk:mockk:1.13.14")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "integrare"
            from(components["java"])
            pom {
                name.set("Integrare")
                description.set(project.property("description").toString())
                url.set("https://github.com/badbabywentaway/Integrare")
                licenses {
                    license {
                        name.set("GNU Lesser General Public License v3.0 or later")
                        url.set("https://www.gnu.org/licenses/lgpl-3.0.html")
                    }
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}
