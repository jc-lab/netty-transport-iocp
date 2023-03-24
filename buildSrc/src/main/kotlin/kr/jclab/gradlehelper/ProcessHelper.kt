package kr.jclab.gradlehelper

import java.lang.ProcessBuilder
import java.util.concurrent.CompletableFuture
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.lang.RuntimeException

object ProcessHelper {
    fun executeCommand(command: List<String>): String {
        val process = ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
        val promise = CompletableFuture<String>()
        val thread = Thread {
            try {
                promise.complete(process.inputStream.bufferedReader().readText().trim())
            } catch (e: IOException) {
                promise.completeExceptionally(e)
            }
        }
        thread.start()
        process.waitFor(10, TimeUnit.SECONDS)
        if (process.exitValue() != 0) {
            throw RuntimeException("exitCode=\${process.exitValue()}")
        }
        return promise.get()
    }
}
