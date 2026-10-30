package com.taandava.manager.util

object CsvParser {
    fun parse(text: String?): List<Array<String>> {
        val out = mutableListOf<Array<String>>()
        if (text == null) return out
        var row = mutableListOf<String>()
        val f = StringBuilder()
        var q = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '"') {
                if (q && i + 1 < text.length && text[i + 1] == '"') {
                    f.append('"')
                    i++
                } else {
                    q = !q
                }
            } else if (c == ',' && !q) {
                row.add(f.toString())
                f.setLength(0)
            } else if ((c == '\n' || c == '\r') && !q) {
                if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') {
                    i++
                }
                row.add(f.toString())
                f.setLength(0)
                if (!(row.size == 1 && row[0].trim().isEmpty())) {
                    out.add(row.toTypedArray())
                }
                row = mutableListOf()
            } else {
                f.append(c)
            }
            i++
        }
        if (f.isNotEmpty() || row.isNotEmpty()) {
            row.add(f.toString())
            out.add(row.toTypedArray())
        }
        return out
    }

    fun escape(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
