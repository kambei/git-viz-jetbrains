plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "dev.kambei"
version = "1.3.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform { 
        intellijIdeaCommunity("2024.2")
        bundledPlugins("Git4Idea")
        instrumentationTools()
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

    // Optional: configure publishing to JetBrains Marketplace
    publishing {
        token.set(System.getenv("MARKETPLACE_TOKEN"))
        // Use the default channel unless overridden (e.g., 'eap')
        channels.set(listOf("default"))
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}