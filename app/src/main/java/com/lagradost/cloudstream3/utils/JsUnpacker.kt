package com.lagradost.cloudstream3.utils

object JsUnpacker {
    fun unpack(script: String): String? {
        val regex = Regex("""eval\(function\(p,a,c,k,e,d\).*?\}\('(.*?)',(\d+),(\d+),'(.*?)'\.split\('\|'\)""", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(script) ?: return script
        return try {
            val payload = match.groupValues[1]
            val radix = match.groupValues[2].toInt()
            val count = match.groupValues[3].toInt()
            val symtab = match.groupValues[4].split("|")

            fun lookup(word: String): String {
                val index = try { word.toInt(radix) } catch (_: Exception) { -1 }
                return if (index in 0 until count && index < symtab.size && symtab[index].isNotEmpty()) {
                    symtab[index]
                } else {
                    word
                }
            }

            Regex("""\b\w+\b""").replace(payload) { m -> lookup(m.value) }
        } catch (_: Exception) {
            script
        }
    }

    fun unpackAndCombine(script: String): String? {
        val unpacked = unpack(script) ?: return script
        return "$script\n$unpacked"
    }
}
