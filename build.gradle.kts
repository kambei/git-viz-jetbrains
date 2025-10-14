plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.10.0"
}

group = "dev.kambei"
version = "1.7.1"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        create("IU", "2025.2.3")
        bundledPlugins("Git4Idea")
    }
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.9.0.202403050737-r")
    testImplementation(kotlin("test"))
}

intellijPlatform {
    pluginConfiguration {
        id.set("dev.kambei.gitviz")
        name.set("GitViz Horizontal")
        version.set(project.version.toString())
        vendor {
            name.set("kambei")
            url.set("https://github.com/kambei/git-viz-jetbrains")
        }
        description = "Shows the Git history horizontally with author, commit message, SHA, tags, and branches."
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}