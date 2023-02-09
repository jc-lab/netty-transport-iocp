plugins {
    id("java")
}

val projectGroup = "kr.jclab.netty"
val projectVersion = "0.0.2"

group = projectGroup
version = projectVersion

allprojects {
    group = projectGroup
    version = projectVersion
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}