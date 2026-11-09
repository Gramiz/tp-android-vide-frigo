package fr.mathieu.videfrigo

import fr.mathieu.videfrigo.data.FoodRules
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FoodRulesTest {
    @Test fun `les saisies invalides sont refusees`() {
        assertNotNull(FoodRules.validate("  ", "2026-11-09"))
        assertNotNull(FoodRules.validate("Lait", "2026-02-30"))
        assertNotNull(FoodRules.validate("a".repeat(81), "2026-11-09"))
        assertNull(FoodRules.validate(" Yaourts ", "2028-02-29"))
    }
    @Test fun `les dates sont comparees en jours civils`() {
        val today = LocalDate.of(2026, 11, 9)
        assertEquals("Date dépassée", FoodRules.dateLabel("2026-11-08", today))
        assertEquals("Aujourd’hui", FoodRules.dateLabel("2026-11-09", today))
        assertEquals("Demain", FoodRules.dateLabel("2026-11-10", today))
        assertEquals("Dans 3 jours", FoodRules.dateLabel("2026-11-12", today))
    }
}
