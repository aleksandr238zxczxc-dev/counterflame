package com.example.counterflame

data class Skin(
    val id: String,
    val name: String,
    val primary: Int,
    val secondary: Int,
    val accent: Int,
    val priceRub: Int,
    val description: String,
    val pattern: PatternType
)

object Skins {
    val all = listOf(
        Skin("default",  "Новичок",     0xFF888888.toInt(), 0xFFAAAAAA.toInt(), 0xFF666666.toInt(), 0,
             "Стартовый комплект", PatternType.SOLID),
        Skin("crimson",  "Crimson Line", 0xFFB71C1C.toInt(), 0xFFEF9A9A.toInt(), 0xFF1A1A1A.toInt(), 199,
             "Красная линия по чёрному", PatternType.GRADIENT),
        Skin("neopixel", "Neo-Pixel",   0xFFFF6D00.toInt(), 0xFFFFE0B2.toInt(), 0xFFFFFFFF.toInt(), 299,
             "Геометрия 80-х", PatternType.STRIPES),
        Skin("plasma",   "Plasma",      0xFFD500F9.toInt(), 0xFF00E5FF.toInt(), 0xFFFFFFFF.toInt(), 449,
             "Плазменный градиент", PatternType.GRADIENT),
        Skin("mercury",  "Mercury",     0xFF9E9E9E.toInt(), 0xFFE0E0E0.toInt(), 0xFF424242.toInt(), 549,
             "Ртутные разводы", PatternType.MARBLE),
        Skin("nebula",   "Nebula",      0xFF7B1FA2.toInt(), 0xFF311B92.toInt(), 0xFF00E5FF.toInt(), 599,
             "Космическая туманность", PatternType.COSMIC),
        Skin("predator", "Predator",    0xFFFF6F00.toInt(), 0xFF1A1A1A.toInt(), 0xFFFFC107.toInt(), 649,
             "Хищные полосы", PatternType.STRIPES),
        Skin("cobalt",   "Cobalt",      0xFF0D47A1.toInt(), 0xFF1976D2.toInt(), 0xFFFFC107.toInt(), 699,
             "Синий кобальт с золотом", PatternType.BLUE_GOLD),
        Skin("aurora",   "Aurora",      0xFF00BFA5.toInt(), 0xFF6200EA.toInt(), 0xFFFFEA00.toInt(), 749,
             "Северное сияние", PatternType.COSMIC),
        Skin("oxide",    "Oxide",       0xFF6D4C41.toInt(), 0xFF8D6E63.toInt(), 0xFFBF360C.toInt(), 349,
             "Ржавый металл", PatternType.RUST)
    )
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all[0]
}
