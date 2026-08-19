package com.mauriciotogneri.fileexplorer.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSectionTest {

    private val defaultOrder = listOf(
        HomeSection.RECENT,
        HomeSection.FAVORITES,
        HomeSection.LOCATIONS,
        HomeSection.STORAGE
    )

    @Test
    fun `default order is the arrangement the home screen shipped with`() {
        assertEquals(defaultOrder, HomeSection.DEFAULT_ORDER)
    }

    @Test
    fun `move sends a section down the list`() {
        val moved = defaultOrder.move(0, 2)

        assertEquals(
            listOf(HomeSection.FAVORITES, HomeSection.LOCATIONS, HomeSection.RECENT, HomeSection.STORAGE),
            moved
        )
    }

    @Test
    fun `move sends a section up the list`() {
        val moved = defaultOrder.move(3, 0)

        assertEquals(
            listOf(HomeSection.STORAGE, HomeSection.RECENT, HomeSection.FAVORITES, HomeSection.LOCATIONS),
            moved
        )
    }

    @Test
    fun `move swaps neighbours`() {
        assertEquals(
            listOf(HomeSection.FAVORITES, HomeSection.RECENT, HomeSection.LOCATIONS, HomeSection.STORAGE),
            defaultOrder.move(0, 1)
        )
    }

    @Test
    fun `move keeps every section exactly once`() {
        val moved = defaultOrder.move(1, 3)

        assertEquals(HomeSection.entries.size, moved.size)
        assertEquals(HomeSection.entries.toSet(), moved.toSet())
    }

    @Test
    fun `move to the same index changes nothing`() {
        assertSame(defaultOrder, defaultOrder.move(2, 2))
    }

    @Test
    fun `move past the end of the list changes nothing`() {
        assertSame(defaultOrder, defaultOrder.move(0, 4))
    }

    @Test
    fun `move from outside the list changes nothing`() {
        assertSame(defaultOrder, defaultOrder.move(-1, 1))
    }

    @Test
    fun `move does not modify the list it was given`() {
        val original = defaultOrder.toList()

        original.move(0, 3)

        assertEquals(defaultOrder, original)
    }

    @Test
    fun `reconcile reads back a stored order`() {
        val stored = listOf("STORAGE", "RECENT", "LOCATIONS", "FAVORITES")

        assertEquals(
            listOf(HomeSection.STORAGE, HomeSection.RECENT, HomeSection.LOCATIONS, HomeSection.FAVORITES),
            HomeSection.reconcile(stored)
        )
    }

    @Test
    fun `reconcile falls back to the default order when nothing is stored`() {
        assertEquals(HomeSection.DEFAULT_ORDER, HomeSection.reconcile(emptyList()))
    }

    @Test
    fun `reconcile falls back to the default order for an empty stored value`() {
        assertEquals(HomeSection.DEFAULT_ORDER, HomeSection.reconcile(listOf("")))
    }

    @Test
    fun `reconcile drops names no section answers to`() {
        val stored = listOf("STORAGE", "CLOUD", "RECENT")

        assertEquals(
            listOf(HomeSection.STORAGE, HomeSection.RECENT, HomeSection.FAVORITES, HomeSection.LOCATIONS),
            HomeSection.reconcile(stored)
        )
    }

    @Test
    fun `reconcile appends sections the stored order left out, keeping the arrangement`() {
        val stored = listOf("STORAGE", "RECENT")

        assertEquals(
            listOf(HomeSection.STORAGE, HomeSection.RECENT, HomeSection.FAVORITES, HomeSection.LOCATIONS),
            HomeSection.reconcile(stored)
        )
    }

    @Test
    fun `reconcile keeps a duplicated name only at its first position`() {
        val stored = listOf("STORAGE", "RECENT", "STORAGE")

        assertEquals(
            listOf(HomeSection.STORAGE, HomeSection.RECENT, HomeSection.FAVORITES, HomeSection.LOCATIONS),
            HomeSection.reconcile(stored)
        )
    }

    @Test
    fun `reconcile always returns every section exactly once`() {
        val reconciled = HomeSection.reconcile(listOf("LOCATIONS", "UNKNOWN", "LOCATIONS"))

        assertEquals(HomeSection.entries.size, reconciled.size)
        assertTrue(reconciled.containsAll(HomeSection.entries))
    }
}
