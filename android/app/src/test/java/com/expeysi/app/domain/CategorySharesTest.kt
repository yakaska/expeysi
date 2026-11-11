package com.expeysi.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CategorySharesTest {

    @Test
    fun `доли в сумме дают единицу`() {
        val shares = CategoryShares.of(listOf(3_000L, 2_000L, 1_000L))
        assertEquals(1f, shares.sum(), 1e-6f)
        assertEquals(0.5f, shares[0], 1e-6f)
        assertEquals(1f / 6, shares[2], 1e-6f)
    }

    @Test
    fun `одна категория занимает весь период`() {
        assertEquals(listOf(1f), CategoryShares.of(listOf(500L)))
    }

    @Test
    fun `ноль расходов даёт нулевые доли`() {
        assertEquals(emptyList<Float>(), CategoryShares.of(emptyList()))
        assertEquals(listOf(0f, 0f), CategoryShares.of(listOf(0L, 0L)))
    }
}
