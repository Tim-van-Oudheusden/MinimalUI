package dev.buildwithtim.minimalui.core

/*
 * Just enough JSON (RFC 8259) for the setup file (#208), so core needs no library for it. Values are Kotlin's own: an
 * object is a Map (in the file's order), an array a List, a number a Double, and true, false and null themselves.
 */

/**
 * The JSON value [text] holds, alone but for whitespace; null when it holds anything else, or JSON nested deeper than
 * the setup file ever is.
 */
internal fun parseJsonOrNull(text: String): Any? =
    try {
        JsonParser(JsonCursor(text)).document()
    } catch (_: MalformedJson) {
        null
    }

/** Thrown inside the parser where the text stops being JSON, and caught as it returns. */
private class MalformedJson(
    message: String,
) : Exception(message)

/** [value] (Maps, Collections, Strings, numbers, Booleans, null) as JSON, indented two spaces a level. */
internal fun jsonText(value: Any?): String = StringBuilder().apply { appendJson(value, "") }.append('\n').toString()

private fun StringBuilder.appendJson(
    value: Any?,
    indent: String,
) {
    when (value) {
        is Map<*, *> -> {
            appendBlock('{', '}', value.entries, indent) { (name, member), inner ->
                appendJsonString(name.toString())
                append(": ")
                appendJson(member, inner)
            }
        }

        is Collection<*> -> {
            appendBlock('[', ']', value, indent) { item, inner -> appendJson(item, inner) }
        }

        is String -> {
            appendJsonString(value)
        }

        else -> {
            append(value)
        }
    }
}

private fun <T> StringBuilder.appendBlock(
    open: Char,
    close: Char,
    items: Collection<T>,
    indent: String,
    appendItem: StringBuilder.(T, String) -> Unit,
) {
    append(open)
    val inner = "$indent  "
    items.forEachIndexed { index, item ->
        append(if (index == 0) "\n" else ",\n").append(inner)
        appendItem(item, inner)
    }
    if (items.isNotEmpty()) append('\n').append(indent)
    append(close)
}

private fun StringBuilder.appendJsonString(text: String) {
    append('"')
    for (char in text) {
        when {
            char == '"' || char == '\\' -> append('\\').append(char)
            char < ' ' -> append("\\u").append(char.code.toString(HEX).padStart(UNICODE_ESCAPE_DIGITS, '0'))
            else -> append(char)
        }
    }
    append('"')
}

private const val HEX = 16
private const val UNICODE_ESCAPE_DIGITS = 4

// Far deeper than the setup file nests (three levels), far shallower than what would overflow the stack.
private const val MAX_DEPTH = 64

private val NUMBER = Regex("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?")

private val ESCAPES =
    mapOf(
        '"' to '"',
        '\\' to '\\',
        '/' to '/',
        'b' to '\b',
        'f' to '\u000C',
        'n' to '\n',
        'r' to '\r',
        't' to '\t',
    )

private val LITERALS = mapOf("true" to true, "false" to false, "null" to null)

/** A recursive-descent reader of one JSON document, through [input]. */
private class JsonParser(
    private val input: JsonCursor,
) {
    fun document(): Any? {
        val value = value(0)
        input.skipSpace()
        if (!input.atEnd) input.fail("trailing text")
        return value
    }

    private fun value(depth: Int): Any? {
        if (depth > MAX_DEPTH) input.fail("nested too deep")
        input.skipSpace()
        return when (input.peek()) {
            '{' -> members(depth + 1)
            '[' -> items(depth + 1)
            '"' -> string()
            't', 'f', 'n' -> literal()
            else -> number()
        }
    }

    private fun members(depth: Int): Map<String, Any?> {
        input.expect('{')
        val members = LinkedHashMap<String, Any?>()
        if (input.consume('}')) return members
        do {
            input.skipSpace()
            if (input.peek() != '"') input.fail("expected a member name")
            val name = string()
            input.expect(':')
            members[name] = value(depth)
        } while (input.consume(','))
        input.expect('}')
        return members
    }

    private fun items(depth: Int): List<Any?> {
        input.expect('[')
        val items = mutableListOf<Any?>()
        if (input.consume(']')) return items
        do {
            items += value(depth)
        } while (input.consume(','))
        input.expect(']')
        return items
    }

    private fun string(): String {
        input.next()
        val text = StringBuilder()
        var char = input.next()
        while (char != '"') {
            when {
                char == '\\' -> text.append(escaped())
                char < ' ' -> input.fail("control character in a string")
                else -> text.append(char)
            }
            char = input.next()
        }
        return text.toString()
    }

    // A \u escape is one UTF-16 unit, so a surrogate pair's two escapes make one character, as JSON spells it.
    private fun escaped(): Char {
        val char = input.next()
        if (char != 'u') return ESCAPES[char] ?: input.fail("unknown escape \\$char")
        val digits = input.take(UNICODE_ESCAPE_DIGITS)
        if (!digits.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) input.fail("bad unicode escape")
        return digits.toInt(HEX).toChar()
    }

    private fun literal(): Any? {
        val word = LITERALS.keys.firstOrNull(input::startsWith) ?: input.fail("unknown word")
        input.take(word.length)
        return LITERALS[word]
    }

    private fun number(): Double = input.take(NUMBER).toDouble()
}

/** A position in [text], read from left to right. */
private class JsonCursor(
    private val text: String,
) {
    private var at = 0

    val atEnd: Boolean get() = at == text.length

    fun skipSpace() {
        while (!atEnd && text[at] in " \t\n\r") at++
    }

    fun peek(): Char = if (atEnd) fail("unexpected end") else text[at]

    fun next(): Char = peek().also { at++ }

    fun startsWith(word: String): Boolean = text.startsWith(word, at)

    /** The next [count] characters. */
    fun take(count: Int): String {
        if (at + count > text.length) fail("unexpected end")
        return text.substring(at, at + count).also { at += count }
    }

    /** The text [pattern] matches right here. */
    fun take(pattern: Regex): String =
        pattern.matchAt(text, at)?.value?.let { take(it.length) } ?: fail("unexpected character")

    /** Whether [char] comes next, after any whitespace; it is passed over when it does. */
    fun consume(char: Char): Boolean {
        skipSpace()
        val found = !atEnd && text[at] == char
        if (found) at++
        return found
    }

    fun expect(char: Char) {
        if (!consume(char)) fail("expected $char")
    }

    fun fail(message: String): Nothing = throw MalformedJson("$message at character $at")
}
