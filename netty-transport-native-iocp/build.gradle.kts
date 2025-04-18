plugins {
    `java-library`
    `maven-publish`
    `signing`
}

val nativeDep by configurations.creating {
    extendsFrom(configurations.implementation.get())
}

configurations {
    compileClasspath.get().extendsFrom(nativeDep)
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8

    withJavadocJar()
    withSourcesJar()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation("org.testng:testng:7.1.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")

    // https://mvnrepository.com/artifact/io.netty/netty-jni-util
    nativeDep("io.netty:netty-jni-util:0.0.6.Final:sources")

    testImplementation("io.netty:netty-buffer:${Version.NETTY}")
    testImplementation("io.netty:netty-transport:${Version.NETTY}")
    testImplementation(project(":netty-transport-classes-iocp"))
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

//val nativeSourceDir = layout.projectDirectory.dir("src/main/c")
//val nativeDepsDir = layout.buildDirectory.dir("native-deps")
//
//tasks.register<Copy>("resolveNativeDep") {
//    shouldRunAfter("dependencies")
//    dependsOn(configurations["compileClasspath"])
//    from(zipTree(nativeDep.singleFile))
//    into(nativeDepsDir)
//}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set(project.name)
                description.set("netty-transport-iocp")
                url.set("https://github.com/jc-lab/netty-transport-iocp")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("jclab")
                        name.set("Joseph Lee")
                        email.set("joseph@jc-lab.net")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/jc-lab/netty-transport-iocp.git")
                    developerConnection.set("scm:git:ssh://git@github.com/jc-lab/netty-transport-iocp.git")
                    url.set("https://github.com/jc-lab/netty-transport-iocp")
                }
            }
        }
    }
    repositories {
        maven {
            val releasesRepoUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            val snapshotsRepoUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            url = uri(if ("$version".endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
            credentials {
                username = findProperty("ossrhUsername") as String?
                password = findProperty("ossrhPassword") as String?
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}

tasks.withType<Sign>().configureEach {
    onlyIf { project.hasProperty("signing.gnupg.keyName") || project.hasProperty("signing.keyId") }
}
