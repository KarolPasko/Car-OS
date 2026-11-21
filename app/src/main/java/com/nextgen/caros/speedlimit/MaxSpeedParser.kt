package com.nextgen.caros.speedlimit

/** Result of interpreting an OpenStreetMap maxspeed value. Never guessed from road type. */
sealed interface ParsedLimit {
    /** A numeric legal limit. [kmh] is always set; [mphSign] is true when the sign shows mph. */
    data class Value(val kmh: Double, val mphSign: Boolean, val signValue: Int, val implicitZone: String? = null) : ParsedLimit
    data object NoLimit : ParsedLimit
    data object Variable : ParsedLimit
    data object Walk : ParsedLimit
    data class Unknown(val raw: String) : ParsedLimit
}

object MaxSpeedParser {
    private const val MPH = 1.609344

    /**
     * Statutory default limits referenced by implicit OSM zone codes. These are the legal values the
     * mapper's tag refers to (see wiki.openstreetmap.org/wiki/Key:maxspeed); codes not listed here
     * are reported as unknown rather than guessed.
     */
    private val zones: Map<String, Pair<Int, Boolean>> = mapOf(
        "GB:nsl_single" to (60 to true), "GB:nsl_dual" to (70 to true), "GB:motorway" to (70 to true),
        "GB:nsl_restricted" to (30 to true),
        "IE:urban" to (50 to false), "IE:rural" to (80 to false), "IE:national" to (100 to false), "IE:motorway" to (120 to false),
        "DE:urban" to (50 to false), "DE:rural" to (100 to false),
        "AT:urban" to (50 to false), "AT:rural" to (100 to false), "AT:motorway" to (130 to false),
        "CH:urban" to (50 to false), "CH:rural" to (80 to false), "CH:trunk" to (100 to false), "CH:motorway" to (120 to false),
        "FR:urban" to (50 to false), "FR:rural" to (80 to false), "FR:motorway" to (130 to false),
        "IT:urban" to (50 to false), "IT:rural" to (90 to false), "IT:trunk" to (110 to false), "IT:motorway" to (130 to false),
        "ES:urban" to (50 to false), "ES:rural" to (90 to false), "ES:motorway" to (120 to false),
        "PT:urban" to (50 to false), "PT:rural" to (90 to false), "PT:motorway" to (120 to false),
        "NL:urban" to (50 to false), "NL:rural" to (80 to false),
        "BE-VLG:urban" to (50 to false), "BE-VLG:rural" to (70 to false), "BE:motorway" to (120 to false),
        "DK:urban" to (50 to false), "DK:rural" to (80 to false), "DK:motorway" to (130 to false),
        "PL:urban" to (50 to false), "PL:rural" to (90 to false), "PL:motorway" to (140 to false),
        "CZ:urban" to (50 to false), "CZ:rural" to (90 to false), "CZ:motorway" to (130 to false),
        "SE:urban" to (50 to false), "SE:rural" to (70 to false),
        "NO:urban" to (50 to false), "NO:rural" to (80 to false),
        "FI:urban" to (50 to false), "FI:rural" to (80 to false),
    )

    private val mphCountries = setOf("GB", "US", "UK")

    fun parse(raw: String?): ParsedLimit? {
        val v = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // Multiple values separated by ';' are conditional / ambiguous – don't pick one.
        if (v.contains(';')) return ParsedLimit.Unknown(v)
        val lower = v.lowercase()
        when (lower) {
            "none" -> return ParsedLimit.NoLimit
            "signals", "variable" -> return ParsedLimit.Variable
            "walk" -> return ParsedLimit.Walk
        }
        Regex("""^(\d{1,3}(?:\.\d+)?)\s*(mph|km/h|kmh|kph)?$""").matchEntire(lower)?.let { m ->
            val n = m.groupValues[1].toDouble()
            val mph = m.groupValues[2] == "mph"
            return ParsedLimit.Value(if (mph) n * MPH else n, mph, n.toInt())
        }
        zones[v]?.let { (n, mph) -> return ParsedLimit.Value(if (mph) n * MPH else n.toDouble(), mph, n, implicitZone = v) }
        if (v.endsWith(":motorway") && v.startsWith("DE")) return ParsedLimit.NoLimit
        // Zone codes such as "DE:zone30", "DE:zone:30", "GB:zone20".
        Regex("""^([A-Z]{2})(?:-[A-Z]+)?:zone:?(\d{1,3})$""").matchEntire(v)?.let { m ->
            val mph = m.groupValues[1] in mphCountries
            val n = m.groupValues[2].toInt()
            return ParsedLimit.Value(if (mph) n * MPH else n.toDouble(), mph, n, implicitZone = v)
        }
        return ParsedLimit.Unknown(v)
    }
}
