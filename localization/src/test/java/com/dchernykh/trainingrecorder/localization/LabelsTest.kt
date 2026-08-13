package com.dchernykh.trainingrecorder.localization

import com.dchernykh.trainingrecorder.core.field.FieldCatalogue
import com.dchernykh.trainingrecorder.core.field.FieldCategory
import com.dchernykh.trainingrecorder.core.sport.Discipline
import com.dchernykh.trainingrecorder.core.sport.SportCatalogue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the seam between the catalogue and the translations. Adding a sport or
 * a field without a label would otherwise show as a blank slot on the watch, and
 * nothing else would notice.
 */
class LabelsTest {
    @Test
    fun everySportHasALabel() {
        val missing = SportCatalogue.all.map { it.id }.filter { Labels.sport(it) == 0 }
        assertTrue(missing.isEmpty(), "sports without a label: $missing")
    }

    @Test
    fun everyDisciplineHasALabel() {
        val missing = Discipline.entries.map { it.id }.filter { Labels.discipline(it) == 0 }
        assertTrue(missing.isEmpty(), "disciplines without a label: $missing")
    }

    @Test
    fun everyFieldCategoryHasALabel() {
        val missing = FieldCategory.entries.map { it.id }.filter { Labels.category(it) == 0 }
        assertTrue(missing.isEmpty(), "categories without a label: $missing")
    }

    @Test
    fun everyDataFieldHasALabel() {
        val missing = FieldCatalogue.all.map { it.id }.filter { Labels.field(it) == 0 }
        assertTrue(missing.isEmpty(), "fields without a label: $missing")
    }

    @Test
    fun anUnknownIdResolvesToNothingRatherThanTheWrongLabel() {
        assertEquals(0, Labels.sport("no_such_sport"))
        assertEquals(0, Labels.discipline("no_such_discipline"))
        assertEquals(0, Labels.category("no_such_category"))
        assertEquals(0, Labels.field("no_such_field"))
    }

    @Test
    fun labelsAreDistinctPerId() {
        val sportIds = SportCatalogue.all.map { Labels.sport(it.id) }
        assertEquals(sportIds.size, sportIds.toSet().size, "two sports share a string resource")
    }
}
