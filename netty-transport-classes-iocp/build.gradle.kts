plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")

    implementation("io.netty:netty-buffer:4.1.87.Final")
    implementation("io.netty:netty-transport:4.1.87.Final")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}