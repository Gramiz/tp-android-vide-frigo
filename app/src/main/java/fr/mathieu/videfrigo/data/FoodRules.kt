package fr.mathieu.videfrigo.data

import java.time.LocalDate

object FoodRules {
    fun validate(name: String, date: String): String? = when {
        name.trim().isEmpty() -> "Indique le nom de l’aliment."
        name.trim().length > 80 -> "Le nom est limité à 80 caractères."
        runCatching { LocalDate.parse(date) }.isFailure -> "Choisis une date valide."
        else -> null
    }

    fun dateLabel(date: String, today: LocalDate = LocalDate.now()): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, LocalDate.parse(date))
        return when {
            days < 0 -> "Date dépassée"
            days == 0L -> "Aujourd’hui"
            days == 1L -> "Demain"
            else -> "Dans $days jours"
        }
    }
}
