package com.expeysi.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Архитектурный guard отвязки от Firebase: импорты Firebase разрешены только
 * в реализации транспорта (data/remote/firebase) и в di. Прямой импорт
 * com.google.firebase.* или внутренней зоны data.remote.firebase.* в любом
 * другом файле валит тест — замена транспорта не разрушается тихо.
 * Сканируются только исходники main: тестовые фейки Firebase не трогают.
 */
class FirebaseImportsGuardTest {

    /** Юнит-тесты Gradle выполняются из каталога модуля app. */
    private val appRoot: File = File(System.getProperty("user.dir"))

    /** Импорты, запрещённые вне data/remote/firebase и di. */
    private val forbidden = listOf(
        "com.google.firebase",
        "com.expe" + "ysi.app.data.remote.firebase",
    )

    @Test
    fun `только в data-remote-firebase и di`() {
        val sourceRoot = File(appRoot, "src/main/java/com/expe" + "ysi" + "/app")
        assertTrue("Каталог исходников не найден: $sourceRoot", sourceRoot.isDirectory)
        val violations = mutableListOf<String>()
        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val relativePath = file.relativeTo(sourceRoot).path.replace('\\', '/')
                if (relativePath.startsWith("data/remote/firebase/") || relativePath.startsWith("di/")) {
                    return@forEach
                }
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("import ") && forbidden.any { trimmed.contains(it) }) {
                            violations += "com/expe" + "ysi/app/$relativePath:${index + 1}"
                        }
                    }
                }
            }
        assertTrue(
            "Firebase-импорты вне data/remote/firebase и di:\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }
}
