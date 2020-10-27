import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.math.BigDecimal.valueOf

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    idea
    `java-library`
//    checkstyle
    jacoco
    eclipse
    `maven-publish`
}

defaultTasks("clean", "build")

project.group = "com.ihsmarkit"
project.version = file("version.txt").readText(Charsets.UTF_8).trim()

val Project.gprUsername: String? get() = this.properties["gprUsername"] as String?
val Project.gprPassword: String? get() = this.properties["gprPassword"] as String?

tasks.jar {
    enabled = false
}

allprojects {
    repositories {
        mavenCentral()
        jcenter()
        mavenLocal()
    }
}

object Versions {
    const val checkstyle = "8.28"
    const val hamcrest = "2.2"
    const val mockito = "3.2.4"
    const val jacoco = "0.8.2"
    const val junit5 = "5.6.0"
    const val logback = "1.2.3"
    const val agrona = "1.3.0"
    const val aeron = "1.27.0"
    const val hdrHistogram = "2.1.12"
}

subprojects {

    apply(plugin = "idea")
    apply(plugin = "java-library")
//    apply(plugin = "checkstyle")
    apply(plugin = "jacoco")
    apply(plugin = "eclipse")
    apply(plugin = "maven-publish")

    group = project.group
    version = project.version

    dependencies {
        testImplementation("org.hamcrest", "hamcrest", Versions.hamcrest)
        testImplementation("org.mockito", "mockito-junit-jupiter", Versions.mockito)
        testImplementation("org.junit.jupiter", "junit-jupiter-api", Versions.junit5)
        testImplementation("org.junit.jupiter", "junit-jupiter-params", Versions.junit5)
        testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", Versions.junit5)

        implementation("ch.qos.logback", "logback-classic", Versions.logback)

    }

    tasks.jar {
        enabled = true
    }

//    checkstyle {
//        toolVersion = Versions.checkstyle
//        sourceSets = singletonList(project.sourceSets.main.get())
//    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        finalizedBy("jacocoTestReport")
    }
    jacoco {
        toolVersion = Versions.jacoco
    }
    tasks.jacocoTestReport {
        reports {
            xml.isEnabled = true
        }
    }

    tasks.compileJava {
        options.encoding = "UTF-8"
        options.isDeprecation = true
    }
    tasks.compileTestJava {
        options.encoding = "UTF-8"
        options.isDeprecation = true
    }
    tasks.test {
        testLogging {
            showStandardStreams = true
            exceptionFormat = TestExceptionFormat.FULL
        }
        systemProperties(mapOf(
            // ADD COMMON SYSTEM PROPERTIES FOR TESTS HERE
            "exampleProperty" to "exampleValue"
        ))
        reports.html.isEnabled = false // Disable individual test reports
    }

    tasks.javadoc {
        title = "<h1>Price Service</h1>"
    }

    publishing {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/stankevichevg/last-price-service")
                credentials {
                    username = project.gprUsername
                    password = project.gprPassword
                }
            }
        }
        publications {
            create<MavenPublication>("LastPriceService") {
                from(components["java"])
                pom {
                    name.set("Last value price service")
                    description.set(
                        """
                            Last value price service.
                            
                            Service for keeping track of the last price for financial instruments.
                            Producers will use the service to publish prices and consumers will use it to obtain them.
                        """
                    )
                    url.set("https://github.com/stankevichevg/last-price-service.git")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("stiachevg")
                            name.set("Evgenii Stankevich")
                            email.set("stankevich.evg@gmail.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/stankevichevg/last-price-service.git")
                        developerConnection.set("scm:git:ssh://github.com/stankevichevg/last-price-service.git")
                        url.set("https://github.com/stankevichevg/last-price-service")
                    }
                }
            }
        }
    }
}

val jacocoAggregateMerge by tasks.creating(JacocoMerge::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    executionData(
        project(":core:common").buildDir.absolutePath + "/jacoco/test.exec",
        project(":core:client").buildDir.absolutePath + "/jacoco/test.exec",
        project(":core:server").buildDir.absolutePath + "/jacoco/test.exec",
        project(":last-price-service:client").buildDir.absolutePath + "/jacoco/test.exec",
        project(":last-price-service:messages").buildDir.absolutePath + "/jacoco/test.exec",
        project(":last-price-service:server").buildDir.absolutePath + "/jacoco/test.exec"
    )
    dependsOn(
        ":core:common:test",
        ":core:client:test",
        ":core:server:test",
        ":last-price-service:client:test",
        ":last-price-service:messages:test",
        ":last-price-service:server:test"
    )
}

@Suppress("UnstableApiUsage")
val jacocoAggregateReport by tasks.creating(JacocoReport::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    executionData(jacocoAggregateMerge.destinationFile)
    reports {
        xml.isEnabled = true
    }
    additionalClassDirs(files(subprojects.flatMap { project ->
        listOf("java", "kotlin").map { project.buildDir.path + "/classes/$it/main" }
    }))
    additionalSourceDirs(files(subprojects.flatMap { project ->
        listOf("java", "kotlin").map { project.file("src/main/$it").absolutePath }
    }))
    dependsOn(jacocoAggregateMerge)
}

tasks {
    jacocoTestCoverageVerification {
        executionData.setFrom(jacocoAggregateMerge.destinationFile)
        violationRules {
            rule {
                limit {
                    minimum = valueOf(0.7)
                }
            }
        }
        additionalClassDirs(files(subprojects.flatMap { project ->
            listOf("java", "kotlin").map { project.buildDir.path + "/classes/$it/main" }
        }))
        additionalSourceDirs(files(subprojects.flatMap { project ->
            listOf("java", "kotlin").map { project.file("src/main/$it").absolutePath }
        }))
        dependsOn(jacocoAggregateReport)
    }
    check {
        finalizedBy(jacocoTestCoverageVerification)
    }
}

project(":core:common") {

    dependencies {
        api("org.agrona", "agrona", Versions.agrona)
    }

}

project(":core:client") {

    dependencies {
        api(project(":core:common"))
        api("io.aeron", "aeron-client", Versions.aeron)
    }

}

project(":core:server") {

    dependencies {
        api(project(":core:common"))
        api("io.aeron", "aeron-client", Versions.aeron)
    }

}

project(":last-price-service:client") {

    dependencies {
        api(project(":core:client"))
        api(project(":last-price-service:messages"))
    }

}

project(":last-price-service:messages") {

    dependencies {
        api(project(":core:common"))
    }

}

project(":last-price-service:server") {

    dependencies {
        api(project(":core:server"))
        implementation(project(":last-price-service:messages"))
    }

}

project(":samples") {

    dependencies {
        implementation(project(":last-price-service:client"))
        implementation(project(":last-price-service:server"))
        implementation("io.aeron", "aeron-driver", Versions.aeron)
        implementation("org.hdrhistogram", "HdrHistogram", Versions.hdrHistogram)
    }

}

tasks.register<Copy>("copyTestLogs") {
    from(".")
    include("**/build/test-output/**")
    include("**/*.log")
    exclude("build")
    into("build/test_logs")
    includeEmptyDirs = false
}