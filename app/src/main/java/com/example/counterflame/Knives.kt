package com.example.counterflame

data class KnifeDef(
    val id: String,
    val name: String,
    val damage: Float,
    val fireRate: Float,
    val priceRub: Int,
    val shape: Int
)

object Knives {
    val all = listOf(
        KnifeDef("flip",      "Flip",      40f, 0.50f,   0, 0),
        KnifeDef("bayonet",   "Bayonet",   50f, 0.50f, 299, 1),
        KnifeDef("karambit",  "Karambit",  60f, 0.45f, 599, 2),
        KnifeDef("butterfly", "Butterfly", 55f, 0.40f, 799, 3),
        KnifeDef("talon",     "Talon",     65f, 0.45f, 999, 4)
    )
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all[0]
}
