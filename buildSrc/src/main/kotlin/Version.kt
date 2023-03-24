import kr.jclab.gradlehelper.ProcessHelper

fun getVersionFromGit(): String {
    return runCatching {
        val version = (
                System.getenv("CI_COMMIT_TAG")
                    ?.takeIf { it.isNotEmpty() }
                    ?: ProcessHelper.executeCommand(listOf("git", "describe", "--tags"))
                        .split("\n")[0]
                )
            .trim()
        if (version.startsWith("v")) {
            version.substring(1)
        } else version
    }.getOrElse {
        return runCatching {
            return ProcessHelper.executeCommand(listOf("git", "rev-parse", "HEAD"))
                .split("\n")[0].trim() + "-SNAPSHOT"
        }.getOrElse {
            return "unknown"
        }
    }
}

object Version {
    val PROJECT by lazy { getVersionFromGit() }
}