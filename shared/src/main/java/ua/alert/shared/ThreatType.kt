package ua.alert.shared

enum class ThreatType(
    val ukrainianLabel: String,
    val instructionText: String,
    val apiCode: String,
    val defaultDangerLevel: DangerLevel
) {
    AIR_RAID(
        ukrainianLabel = "Повітряна тривога!",
        instructionText = "Негайно прямуйте до найближчого укриття!",
        apiCode = "AIR",
        defaultDangerLevel = DangerLevel.RED
    ),
    ARTILLERY(
        ukrainianLabel = "Загроза артобстрілу!",
        instructionText = "Негайно прямуйте до укриття!",
        apiCode = "ARTILLERY",
        defaultDangerLevel = DangerLevel.RED
    ),
    URBAN_FIGHTS(
        ukrainianLabel = "Загроза вуличних боїв!",
        instructionText = "Залишайтеся в безпечному місці!",
        apiCode = "URBAN_FIGHTS",
        defaultDangerLevel = DangerLevel.RED
    ),
    CHEMICAL(
        ukrainianLabel = "Хімічна небезпека!",
        instructionText = "Щільно зачиніть вікна та двері!",
        apiCode = "CHEMICAL",
        defaultDangerLevel = DangerLevel.YELLOW
    ),
    NUCLEAR(
        ukrainianLabel = "Радіаційна небезпека!",
        instructionText = "Залишайтеся в укритті!",
        apiCode = "NUCLEAR",
        defaultDangerLevel = DangerLevel.YELLOW
    ),
    ALL_CLEAR(
        ukrainianLabel = "Відбій тривоги",
        instructionText = "Увага! Відбій повітряної тривоги",
        apiCode = "ALL_CLEAR",
        defaultDangerLevel = DangerLevel.GREEN
    ),
    UNKNOWN(
        ukrainianLabel = "Небезпека!",
        instructionText = "Негайно прямуйте до укриття!",
        apiCode = "UNKNOWN",
        defaultDangerLevel = DangerLevel.RED
    );

    companion object {
        fun fromApiType(rawType: String?): ThreatType {
            if (rawType.isNullOrBlank()) return AIR_RAID
            val normalized = rawType.trim().uppercase()
            return when (normalized) {
                "AIR", "AIR_RAID" -> AIR_RAID
                "ARTILLERY" -> ARTILLERY
                "URBAN", "URBAN_FIGHTS" -> URBAN_FIGHTS
                "CHEMICAL" -> CHEMICAL
                "NUCLEAR", "RADIATION" -> NUCLEAR
                "NONE", "ALL_CLEAR", "CLEAR" -> ALL_CLEAR
                else -> entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) } ?: AIR_RAID
            }
        }
    }
}
