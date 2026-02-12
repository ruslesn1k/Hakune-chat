plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "com.hakune"
version = "2.1.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.bstats:bstats-bukkit:3.0.2")
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("org.bstats", "com.hakune.chat.libs.bstats")
    }
}
