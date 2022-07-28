package com.jet.android.links

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CommandTest {
    @Test
    fun testDelegateAssignmentsParseUriParts() {
        // Given
        val command = TestCommand()

        // When
        command.uri = Uri.parse("http://just-eat.co.uk/data/restaurants/123?do=C&ray=D&mi=E&fa=F")

        // Then
        assertEquals("data", command.dataSlug)
        assertEquals("restaurants", command.restaurantsSlug)
        assertEquals("123", command.restaurantIdSlug)
        assertEquals("C", command.doQueryParam)
        assertEquals("D", command.rayQueryParam)
        assertEquals("E", command.miQueryParam)
        assertEquals("F", command.faQueryParam)
    }

    class TestCommand : Command() {
        val dataSlug by pathSegment(0)
        val restaurantsSlug by pathSegment(1)
        val restaurantIdSlug by pathSegment(2)
        val doQueryParam by queryParam("do")
        val rayQueryParam by queryParam("ray")
        val miQueryParam by queryParam("mi")
        val faQueryParam by queryParam("fa")

        override fun execute() {}
    }
}
