package com.powersync

import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream

private class R

internal fun extractLib(fileName: String): Path {
    val os = System.getProperty("os.name").lowercase()
    val (prefix, extension) = when {
        os.contains("win") -> "" to "dll"
        os.contains("nux") || os.contains("nix") || os.contains("aix") -> "lib" to "so"
        os.contains("mac") -> "lib" to "dylib"
        else -> error("Unsupported OS: $os")
    }

    val path = "/$prefix$fileName.$extension"

    val tmpPath = createTempFile(prefix, ".$extension")
    Runtime.getRuntime().addShutdownHook(Thread { tmpPath.deleteIfExists() })

    (R::class.java.getResourceAsStream(path) ?: error("Resource $path not found")).use { input ->
        tmpPath.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    return tmpPath
}
