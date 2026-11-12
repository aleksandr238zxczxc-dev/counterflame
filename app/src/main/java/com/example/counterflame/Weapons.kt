package com.example.counterflame

data class WeaponDef(
    val id: String,
    val name: String,
    val damage: Float,
    val fireRate: Float,
    val magSize: Int,
    val reloadTime: Float,
    val range: Float,
    val sprite: Int
)

object Weapons {
    val all = listOf(
        WeaponDef("pistol",  "Glock", 18f, 0.14f, 20, 1.2f, 20f, 0),
        WeaponDef("smg",     "MP5",   22f, 0.08f, 30, 1.6f, 18f, 1),
        WeaponDef("rifle",   "AK-47", 34f, 0.12f, 30, 1.8f, 26f, 2),
        WeaponDef("rifle2",  "M4A1",  28f, 0.10f, 30, 1.8f, 26f, 3),
        WeaponDef("sniper",  "AWP",   90f, 1.10f, 10, 2.6f, 40f, 4)
    )
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all[0]
}
