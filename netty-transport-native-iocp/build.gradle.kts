plugins {
    id("java-library")
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

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation("org.testng:testng:7.1.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")

    // https://mvnrepository.com/artifact/io.netty/netty-jni-util
//    implementation("io.netty:netty-jni-util:0.0.6.Final:sources")
    nativeDep("io.netty:netty-jni-util:0.0.6.Final:sources")

    testImplementation("io.netty:netty-buffer:4.1.87.Final")
    testImplementation("io.netty:netty-transport:4.1.87.Final")
    testImplementation(project(":netty-transport-classes-iocp"))
}

val nativeSourceDir = layout.projectDirectory.dir("src/main/c")
val nativeDepsDir = layout.buildDirectory.dir("native-deps")

tasks.register<Copy>("resolveNativeDep") {
    shouldRunAfter("dependencies")
    dependsOn(configurations["compileClasspath"])
    from(zipTree(nativeDep.singleFile))
    into(nativeDepsDir)
}

tasks.create("buildNativeWinX86_32") {
    shouldRunAfter("resolveNativeDep")
    dependsOn("build")
    val workingDirectory = layout.buildDirectory.dir("native-build-windows-x86_32").get()
    mkdir(workingDirectory)
    doLast {
        ProcessBuilder()
            .command("${layout.projectDirectory}/build-native.bat", "Win32", nativeDepsDir.get().toString(), nativeSourceDir.toString())
            .directory(file(workingDirectory))
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
            .waitFor(600, TimeUnit.SECONDS)
    }
}

tasks.create("buildNativeWinX86_64") {
    shouldRunAfter("resolveNativeDep")
    dependsOn("build")
    val workingDirectory = layout.buildDirectory.dir("native-build-windows-x86_64").get()
    mkdir(workingDirectory)
    doLast {
        ProcessBuilder()
            .command("${layout.projectDirectory}/build-native.bat", "Win32", nativeDepsDir.get().toString(), nativeSourceDir.toString())
            .directory(file(workingDirectory))
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
            .waitFor(600, TimeUnit.SECONDS)
    }
}

//tasks.getByName("compileCpp") {
//    dependsOn("processResources")
//}

//tasks.getByName<Test>("test") {
//    println("LIBRARY PATH: ${layout.buildDirectory.dir("libs/main/x86-64/").get().toString()}")
//    systemProperty("java.library.path", layout.buildDirectory.dir("libs/main/x86-64/").get().toString())
//}

//application {
//    applicationDefaultJvmArgs = ["-Djava.library.path=" + layout.buildDirectory.dir("libs/main/x86-64/").get().toString()]
//}