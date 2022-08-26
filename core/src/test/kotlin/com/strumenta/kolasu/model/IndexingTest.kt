package com.strumenta.kolasu.model

import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class IndexingTest {

    @Test
    fun computeIdsWithDefaultWalkerAndIdProvider() {
        val a1 = A(s = "a1")
        val a2 = A(s = "a2")
        val a3 = A(s = "a3")
        val b1 = B(a = a1, manyAs = listOf(a2, a3))
        val idsMap = b1.computeIds(idProvider = SequentialIdProvider())
        assertEquals(4, idsMap.size)
        assertContains(idsMap, a1)
        assertContains(idsMap, a2)
        assertContains(idsMap, a3)
        assertContains(idsMap, b1)
    }

    @Test
    fun computeIdsWithCustomWalkerAndDefaultIdProvider() {
        val a1 = A(s = "a1")
        val a2 = A(s = "a2")
        val a3 = A(s = "a3")
        val b1 = B(a = a1, manyAs = listOf(a2, a3))
        val ids = b1.computeIds(walker = Node::walkLeavesFirst, idProvider = SequentialIdProvider())
        assertEquals(4, ids.size)
        assertContains(ids, a1)
        assertContains(ids, a2)
        assertContains(ids, a3)
        assertContains(ids, b1)
    }

    @Test
    fun computeIdsWithDefaultWalkerAndCustomIdProvider() {
        val a1 = A(s = "a1")
        val a2 = A(s = "a2")
        val a3 = A(s = "a3")
        val b1 = B(a = a1, manyAs = listOf(a2, a3))
        val ids = b1.computeIds(
            idProvider = object : IdProvider {
                private var counter: Int = 0
                override fun getId(node: Node): String? {
                    return "custom_${this.counter++}"
                }
            }
        )
        assertEquals(4, ids.size)
        assertEquals(ids[b1], "custom_0")
        assertEquals(ids[a1], "custom_1")
        assertEquals(ids[a2], "custom_2")
        assertEquals(ids[a3], "custom_3")
    }

    @Test
    fun computeIdsWithCustomWalkerAndIdProvider() {
        val a1 = A(s = "a1")
        val a2 = A(s = "a2")
        val a3 = A(s = "a3")
        val b1 = B(a = a1, manyAs = listOf(a2, a3))
        val ids = b1.computeIds(
            walker = Node::walkLeavesFirst,
            idProvider = object : IdProvider {
                private var counter: Int = 0
                override fun getId(node: Node): String? {
                    return "custom_${this.counter++}"
                }
            }
        )
        assertEquals(4, ids.size)
        assertEquals(ids[a1], "custom_0")
        assertEquals(ids[a2], "custom_1")
        assertEquals(ids[a3], "custom_2")
        assertEquals(ids[b1], "custom_3")
        print(ids)
    }
}
