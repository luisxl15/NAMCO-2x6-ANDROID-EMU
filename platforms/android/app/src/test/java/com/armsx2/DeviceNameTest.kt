package com.armsx2

import com.armsx2.diag.DeviceName
import com.armsx2.diag.SysProp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that turn a part number into the name on the box.
 *
 * The case that started this is the real one: a Redmi that reports `23100RN82L` and nothing else,
 * so the overlay named the phone with a string its owner had never seen.
 */
class DeviceNameTest {

    private fun props(vararg pairs: Pair<String, String>): (String) -> String? {
        val map = pairs.toMap()
        return { key -> map[key] }
    }

    @Test
    fun `takes the vendor marketing name over the model code`() {
        val name = DeviceName.pick(
            model = "23100RN82L",
            property = props("ro.product.marketname" to "Redmi Note 13 5G"),
        )
        assertEquals("Redmi Note 13 5G", name)
    }

    @Test
    fun `keeps looking past a placeholder`() {
        val name = DeviceName.pick(
            model = "CPH2451",
            property = props(
                "ro.product.marketname" to "unknown",
                "ro.vendor.oplus.market.name" to "OnePlus 11",
            ),
        )
        assertEquals("OnePlus 11", name)
    }

    @Test
    fun `a property that only repeats the model is not a name`() {
        val name = DeviceName.pick(
            model = "SM-S918B",
            property = props("ro.product.marketname" to "SM-S918B"),
        )
        assertEquals("", name)
    }

    @Test
    fun `nothing to read leaves the caller with the model`() {
        assertEquals("", DeviceName.pick(model = "Pixel 8", property = { null }))
    }

    @Test
    fun `a value with no letters in it is not a name`() {
        assertFalse(DeviceName.usable("2201123", "M2101K6G"))
        assertFalse(DeviceName.usable("   ", "M2101K6G"))
        assertTrue(DeviceName.usable("Xiaomi 12", "M2101K6G"))
    }

    @Test
    fun `the report keeps the model code next to the name`() {
        assertEquals(
            "Xiaomi Redmi Note 13 5G (23100RN82L)",
            DeviceName.describe("Xiaomi", "23100RN82L", "Redmi Note 13 5G"),
        )
    }

    @Test
    fun `the maker is not repeated when the name already carries it`() {
        assertEquals(
            "Xiaomi 14 (23127PN0CG)",
            DeviceName.describe("Xiaomi", "23127PN0CG", "Xiaomi 14"),
        )
    }

    @Test
    fun `a phone whose model is already readable gets no parenthesis`() {
        assertEquals("Google Pixel 8", DeviceName.describe("Google", "Pixel 8", ""))
        assertEquals("samsung SM-S918B", DeviceName.describe("samsung", "SM-S918B", "SM-S918B"))
    }

    @Test
    fun `getprop output is read back as pairs`() {
        val map = SysProp.parse(
            """
            [ro.product.brand]: [Redmi]
            [ro.product.marketname]: [Redmi Note 13 5G]
            [ro.build.id]: []
            not a property line
            """.trimIndent(),
        )
        assertEquals("Redmi Note 13 5G", map["ro.product.marketname"])
        assertEquals("", map["ro.build.id"])
        assertEquals(3, map.size)
    }
}
