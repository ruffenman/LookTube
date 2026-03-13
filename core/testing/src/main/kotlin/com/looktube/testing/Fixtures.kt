package com.looktube.testing

object Fixtures

fun loadFixture(path: String): String {
    val stream = Fixtures::class.java.classLoader.getResourceAsStream(path)
        ?: error("Fixture not found: $path")
    return stream.bufferedReader().use { it.readText() }
}
