package dev.buildwithtim.minimalui.tooling

/**
 * Reads the YAML subset this repository's workflows and configs use: block mappings and sequences, plain and quoted
 * scalars, flow sequences, block scalars (`|`, `>`, with `-`/`+` chomping) and comments. Mappings become
 * [Map]s, sequences [List]s, and scalars [String], [Int], [Boolean] or null.
 *
 * Constructs outside the subset (anchors, flow mappings, multi-line plain scalars, tabs) throw instead of being
 * misread, so a workflow edit that needs them fails loudly and this reader gets extended on purpose.
 */
object Yaml {
    fun parse(text: String): Any? {
        val lines = text.lines().toMutableList()
        // lines() yields a final "" for a trailing newline; it is that line's break, not an empty line.
        val finalBreak = text.endsWith("\n")
        if (finalBreak) lines.removeAt(lines.lastIndex)
        val reader = Reader(lines, finalBreak)
        val first = reader.nextStructural() ?: return null
        val node = reader.node(lines.indentOf(first))
        reader.nextStructural()?.let { reader.fail(it, "unexpected content after the document") }
        return node
    }

    private class Reader(
        val lines: MutableList<String>,
        val finalBreak: Boolean,
    ) {
        var pos = 0

        fun fail(
            line: Int,
            message: String,
        ): Nothing = throw IllegalArgumentException("YAML line ${line + 1}: $message: '${lines[line].trim()}'")

        /** Index of the next line that is neither blank nor a comment, leaving [pos] on it. */
        fun nextStructural(): Int? {
            while (pos < lines.size) {
                val trimmed = lines[pos].trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    if (lines[pos].trimStart(' ').startsWith("\t")) fail(pos, "tab indentation")
                    if (trimmed == "---" || trimmed == "...") fail(pos, "document markers are not supported")
                    return pos
                }
                pos++
            }
            return null
        }

        fun node(indent: Int): Any? {
            val line = nextStructural() ?: return null
            val content = lines[line].trim()
            return if (content == "-" || content.startsWith("- ")) sequence(indent) else mapping(indent)
        }

        fun sequence(indent: Int): List<Any?> {
            val items = mutableListOf<Any?>()
            while (true) {
                val line = nextStructural() ?: break
                val content = lines[line].trim()
                if (lines.indentOf(line) != indent || !(content == "-" || content.startsWith("- "))) break
                val item = content.removePrefix("-").trimStart(' ')
                if (item.isEmpty() || item.startsWith("#")) {
                    pos++
                    val child = nextStructural()
                    items += if (child != null && lines.indentOf(child) > indent) node(lines.indentOf(child)) else null
                } else if (keyEnd(item) != null) {
                    // "- key: value" opens a mapping whose keys sit at the column of "key".
                    val itemIndent = indent + (content.length - item.length)
                    lines[line] = " ".repeat(itemIndent) + item
                    items += mapping(itemIndent)
                } else {
                    items += valueAt(line, item, indent)
                }
            }
            return items
        }

        fun mapping(indent: Int): Map<String, Any?> {
            val entries = linkedMapOf<String, Any?>()
            while (true) {
                val line = nextStructural() ?: break
                val content = lines[line].trim()
                val lineIndent = lines.indentOf(line)
                if (lineIndent < indent) break
                if (lineIndent > indent) fail(line, "unexpected indentation")
                if (content == "-" || content.startsWith("- ")) break
                val end = keyEnd(content) ?: fail(line, "expected 'key: value'")
                val key = unquote(content.substring(0, end).trim(), line)
                if (key in entries) fail(line, "duplicate key '$key'")
                val rest = content.substring(end + 1).trim()
                entries[key] =
                    if (rest.isEmpty() || rest.startsWith("#")) {
                        pos++
                        val child = nextStructural()
                        when {
                            child == null -> {
                                null
                            }

                            lines.indentOf(child) > indent -> {
                                node(lines.indentOf(child))
                            }

                            // A sequence may sit at its key's own indentation.
                            lines.indentOf(
                                child,
                            ) == indent && lines[child].trim().let { it == "-" || it.startsWith("- ") } -> {
                                sequence(indent)
                            }

                            else -> {
                                null
                            }
                        }
                    } else {
                        valueAt(line, rest, indent)
                    }
            }
            return entries
        }

        /** Parses the inline [text] that starts on [line], consuming any block scalar body below it. */
        fun valueAt(
            line: Int,
            text: String,
            parentIndent: Int,
        ): Any? {
            pos = line + 1
            if (text.startsWith("|") || text.startsWith(">")) return blockScalar(line, text, parentIndent)
            val value = scalar(text, line)
            val next = nextStructural()
            if (next != null && lines.indentOf(next) > parentIndent && lines.indentOf(next) > lines.indentOf(line)) {
                fail(next, "multi-line plain scalars are not supported")
            }
            return value
        }

        fun blockScalar(
            line: Int,
            header: String,
            parentIndent: Int,
        ): String {
            val indicator = stripComment(header)
            if (!Regex("[|>][-+]?").matches(indicator)) fail(line, "unsupported block scalar header '$indicator'")
            val body = mutableListOf<String>()
            var blockIndent: Int? = null
            while (pos < lines.size) {
                val raw = lines[pos]
                if (raw.isBlank()) {
                    body += ""
                    pos++
                    continue
                }
                val rawIndent = raw.length - raw.trimStart(' ').length
                if (rawIndent <= parentIndent) break
                val indent = blockIndent ?: rawIndent.also { blockIndent = it }
                if (rawIndent < indent) break
                body += raw.substring(indent)
                pos++
            }
            val trailingBlanks = body.takeLastWhile { it.isEmpty() }.size
            val content = body.dropLast(trailingBlanks)
            val joined = if (indicator.startsWith("|")) content.joinToString("\n") else fold(content)
            // The last body line only has a line break if more input follows or the file ends with one.
            val lastBreak = if (pos < lines.size || finalBreak) 1 else 0
            return when {
                content.isEmpty() -> ""
                indicator.endsWith("-") -> joined
                indicator.endsWith("+") -> joined + "\n".repeat(trailingBlanks + lastBreak)
                trailingBlanks + lastBreak > 0 -> joined + "\n"
                else -> joined
            }
        }

        /**
         * Folds `>` content: a single break between two text lines becomes a space, and k empty lines between them
         * become k newlines. Around a more-indented line every break is kept.
         */
        fun fold(content: List<String>): String =
            buildString {
                var previous: String? = null
                var blanks = 0
                for (text in content) {
                    if (text.isEmpty()) {
                        blanks++
                        continue
                    }
                    val moreIndented = previous?.startsWith(" ") == true || text.startsWith(" ")
                    when {
                        previous == null -> append("\n".repeat(blanks))
                        moreIndented -> append("\n".repeat(blanks + 1))
                        blanks == 0 -> append(' ')
                        else -> append("\n".repeat(blanks))
                    }
                    append(text)
                    previous = text
                    blanks = 0
                }
            }

        fun scalar(
            text: String,
            line: Int,
        ): Any? {
            val trimmed = text.trim()
            return when {
                trimmed.startsWith("&") || trimmed.startsWith("*") -> {
                    fail(line, "anchors and aliases are not supported")
                }

                trimmed.startsWith("{") -> {
                    fail(line, "flow mappings are not supported")
                }

                trimmed.startsWith("[") -> {
                    flowSequence(trimmed, line)
                }

                trimmed.startsWith("'") || trimmed.startsWith("\"") -> {
                    val end = quotedEnd(trimmed, line)
                    val rest = trimmed.substring(end + 1).trim()
                    if (rest.isNotEmpty() && !rest.startsWith("#")) fail(line, "text after a quoted scalar")
                    unquote(trimmed.substring(0, end + 1), line)
                }

                else -> {
                    plain(stripComment(trimmed))
                }
            }
        }

        fun flowSequence(
            text: String,
            line: Int,
        ): List<Any?> {
            val close = text.lastIndexOf(']')
            if (close < 0) fail(line, "unterminated flow sequence")
            val rest = text.substring(close + 1).trim()
            if (rest.isNotEmpty() && !rest.startsWith("#")) fail(line, "text after a flow sequence")
            val inner = text.substring(1, close)
            if (inner.isBlank()) return emptyList()
            val items = mutableListOf<String>()
            var current = StringBuilder()
            var i = 0
            while (i < inner.length) {
                val c = inner[i]
                when (c) {
                    '\'', '"' -> {
                        val end = i + quotedEnd(inner.substring(i), line)
                        current.append(inner, i, end + 1)
                        i = end
                    }

                    '[', '{' -> {
                        fail(line, "nested flow collections are not supported")
                    }

                    ',' -> {
                        items += current.toString()
                        current = StringBuilder()
                    }

                    else -> {
                        current.append(c)
                    }
                }
                i++
            }
            items += current.toString()
            return items.map { item ->
                val trimmed = item.trim()
                if (trimmed.startsWith("'") || trimmed.startsWith("\"")) unquote(trimmed, line) else plain(trimmed)
            }
        }

        fun quotedEnd(
            text: String,
            line: Int,
        ): Int {
            val quote = text[0]
            var i = 1
            while (i < text.length) {
                val c = text[i]
                if (quote == '"' && c == '\\') {
                    i += 2
                    continue
                }
                if (c == quote) {
                    if (quote == '\'' && i + 1 < text.length && text[i + 1] == '\'') {
                        i += 2
                        continue
                    }
                    return i
                }
                i++
            }
            fail(line, "unterminated quoted scalar")
        }

        fun unquote(
            text: String,
            line: Int,
        ): String =
            when {
                text.length >= 2 && text.startsWith("'") && text.endsWith("'") -> {
                    text.substring(1, text.length - 1).replace("''", "'")
                }

                text.length >= 2 && text.startsWith("\"") && text.endsWith("\"") -> {
                    unescape(text.substring(1, text.length - 1), line)
                }

                else -> {
                    text
                }
            }

        fun unescape(
            text: String,
            line: Int,
        ): String =
            buildString {
                var i = 0
                while (i < text.length) {
                    val c = text[i]
                    if (c != '\\') {
                        append(c)
                        i++
                        continue
                    }
                    if (i + 1 >= text.length) fail(line, "dangling escape")
                    append(
                        when (val e = text[i + 1]) {
                            'n' -> '\n'
                            't' -> '\t'
                            '"' -> '"'
                            '\\' -> '\\'
                            '/' -> '/'
                            else -> fail(line, "unsupported escape '\\$e'")
                        },
                    )
                    i += 2
                }
            }
    }

    /** Position of the ':' that ends a mapping key in [content], or null if it is not a "key: value" line. */
    private fun keyEnd(content: String): Int? {
        if (content.startsWith("'") || content.startsWith("\"")) {
            val close = content.indexOf(content[0], 1)
            val endsKey = close > 0 && content.getOrNull(close + 1) == ':' && isValueStart(content, close + 2)
            return if (endsKey) close + 1 else null
        }
        var i = 0
        while (i < content.length) {
            if (content[i] == ':' && isValueStart(content, i + 1)) return i
            if (content[i] == ' ' && content.getOrNull(i + 1) == '#') return null
            i++
        }
        return null
    }

    private fun isValueStart(
        content: String,
        index: Int,
    ) = index >= content.length || content[index] == ' '

    private fun stripComment(text: String): String {
        val hash = Regex("(^|\\s)#").find(text) ?: return text.trim()
        return text.substring(0, hash.range.first).trim()
    }

    private fun plain(text: String): Any? =
        when {
            text.isEmpty() || text == "~" || text == "null" -> null
            text == "true" -> true
            text == "false" -> false
            Regex("-?[0-9]+").matches(text) -> text.toInt()
            else -> text
        }

    private fun List<String>.indentOf(line: Int): Int = this[line].length - this[line].trimStart(' ').length
}
