/*
 * Copyright KG Soft
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kgurgul.openksef.domain.pdf

/**
 * A single element of a parsed XML tree. Namespace prefixes are stripped from element and attribute
 * names because KSeF FA documents use a single default namespace.
 */
internal class XmlNode(
    val name: String,
    val attributes: Map<String, String>,
    val children: List<XmlNode>,
    val text: String,
) {
    fun child(name: String): XmlNode? = children.firstOrNull { it.name == name }

    fun children(name: String): List<XmlNode> = children.filter { it.name == name }

    fun childText(name: String): String? = child(name)?.text?.takeIf { it.isNotEmpty() }
}

/**
 * A minimal, dependency-free XML reader sufficient for the well-formed, machine-generated XML that
 * KSeF returns. It is intentionally lenient (it does not validate close-tag names) and supports the
 * subset of XML found in FA invoices: elements, attributes, text, CDATA, comments and the standard
 * five entities plus numeric character references.
 */
internal object MiniXml {

    fun parse(input: String): XmlNode = Parser(input).parseDocument()

    private class Parser(private val s: String) {
        private var i = 0

        fun parseDocument(): XmlNode {
            skipMisc()
            return parseElement()
        }

        private fun skipMisc() {
            while (i < s.length) {
                skipWhitespace()
                when {
                    i >= s.length -> return
                    startsWith("<?") -> skipUntil("?>")
                    startsWith("<!--") -> skipUntil("-->")
                    startsWith("<!") -> skipUntil(">")
                    else -> return
                }
            }
        }

        private fun parseElement(): XmlNode {
            expect('<')
            val rawName = readName()
            val attrs = parseAttributes()
            skipWhitespace()
            if (startsWith("/>")) {
                i += 2
                return XmlNode(localName(rawName), attrs, emptyList(), "")
            }
            expect('>')

            val children = ArrayList<XmlNode>()
            val text = StringBuilder()
            while (true) {
                if (i >= s.length) error("Unexpected end of XML while reading <$rawName>")
                when {
                    startsWith("</") -> {
                        i += 2
                        readName()
                        skipWhitespace()
                        expect('>')
                        break
                    }
                    startsWith("<!--") -> skipUntil("-->")
                    startsWith("<![CDATA[") -> {
                        i += 9
                        val end = s.indexOf("]]>", i)
                        val stop = if (end == -1) s.length else end
                        text.append(s, i, stop)
                        i = if (end == -1) s.length else end + 3
                    }
                    startsWith("<?") -> skipUntil("?>")
                    s[i] == '<' -> children.add(parseElement())
                    else -> {
                        val start = i
                        while (i < s.length && s[i] != '<') i++
                        text.append(decodeEntities(s.substring(start, i)))
                    }
                }
            }
            return XmlNode(localName(rawName), attrs, children, text.toString().trim())
        }

        private fun parseAttributes(): Map<String, String> {
            val map = LinkedHashMap<String, String>()
            while (true) {
                skipWhitespace()
                if (i >= s.length) break
                val c = s[i]
                if (c == '>' || c == '/' || c == '?') break
                val name = readName()
                skipWhitespace()
                if (i < s.length && s[i] == '=') {
                    i++
                    skipWhitespace()
                    if (i >= s.length) break
                    val quote = s[i]
                    i++
                    val start = i
                    while (i < s.length && s[i] != quote) i++
                    map[localName(name)] = decodeEntities(s.substring(start, i))
                    if (i < s.length) i++ // consume closing quote
                } else {
                    map[localName(name)] = ""
                }
            }
            return map
        }

        private fun readName(): String {
            val start = i
            while (i < s.length) {
                val c = s[i]
                if (c.isWhitespace() || c == '>' || c == '/' || c == '=' || c == '?') break
                i++
            }
            return s.substring(start, i)
        }

        private fun localName(name: String): String {
            val idx = name.indexOf(':')
            return if (idx >= 0) name.substring(idx + 1) else name
        }

        private fun skipWhitespace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun startsWith(prefix: String): Boolean = s.startsWith(prefix, i)

        private fun skipUntil(marker: String) {
            val idx = s.indexOf(marker, i)
            i = if (idx == -1) s.length else idx + marker.length
        }

        private fun expect(c: Char) {
            if (i >= s.length || s[i] != c) error("Expected '$c' at position $i")
            i++
        }
    }

    private fun decodeEntities(raw: String): String {
        if (!raw.contains('&')) return raw
        val sb = StringBuilder(raw.length)
        var idx = 0
        while (idx < raw.length) {
            val c = raw[idx]
            if (c != '&') {
                sb.append(c)
                idx++
                continue
            }
            val semi = raw.indexOf(';', idx)
            val entity = if (semi == -1) null else raw.substring(idx + 1, semi)
            val decoded =
                when {
                    entity == null -> null
                    entity == "amp" -> "&"
                    entity == "lt" -> "<"
                    entity == "gt" -> ">"
                    entity == "quot" -> "\""
                    entity == "apos" -> "'"
                    entity.startsWith("#x") || entity.startsWith("#X") ->
                        entity.substring(2).toIntOrNull(16)?.let(::codePointToString)
                    entity.startsWith("#") ->
                        entity.substring(1).toIntOrNull()?.let(::codePointToString)
                    else -> null
                }
            if (decoded != null) {
                sb.append(decoded)
                idx = semi + 1
            } else {
                sb.append(c)
                idx++
            }
        }
        return sb.toString()
    }

    private fun codePointToString(codePoint: Int): String =
        if (codePoint in 0..0xFFFF) {
            codePoint.toChar().toString()
        } else {
            val v = codePoint - 0x10000
            charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar())
                .concatToString()
        }
}
