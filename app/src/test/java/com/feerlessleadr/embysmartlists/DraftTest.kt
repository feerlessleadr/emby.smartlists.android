package com.feerlessleadr.embysmartlists

import com.feerlessleadr.embysmartlists.data.AppJson
import com.feerlessleadr.embysmartlists.data.AppSettings
import com.feerlessleadr.embysmartlists.data.Catalog
import com.feerlessleadr.embysmartlists.data.ListDraft
import com.feerlessleadr.embysmartlists.data.SmartListDoc
import com.feerlessleadr.embysmartlists.data.Templates
import com.feerlessleadr.embysmartlists.data.ThemeMode
import com.feerlessleadr.embysmartlists.data.fieldsFor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftTest {
    private val catalog: Catalog = AppJson.decodeFromString(
        Catalog.serializer(),
        """
        {"ApiVersion":1,
         "Fields":[
           {"Name":"NextUnwatched","Label":"Next Unwatched","Category":"RatingsPlayback","Type":"Boolean","Operators":["Equal"]},
           {"Name":"LibraryName","Label":"Library Name","Category":"Library","Type":"Text","Operators":["Equal","IsIn"]},
           {"Name":"Name","Label":"Name","Category":"Content","Type":"Text","Operators":["Equal"]},
           {"Name":"Resolution","Label":"Resolution","Category":"Video","Type":"Resolution","Operators":["Equal"]},
           {"Name":"AudioCodec","Label":"Audio Codec","Category":"Audio","Type":"Text","Operators":["Equal"]}],
         "Operators":[{"Value":"Equal","Label":"equals"},{"Value":"IsIn","Label":"is in"}],
         "Sorts":[{"Value":"Name","Label":"Name"},
                  {"Value":"Round Robin","Label":"Round Robin (Interleave)","Group":"Round Robin","RoundRobin":true}]}
        """.trimIndent(),
    )

    private val existing = SmartListDoc.parse(
        """{"Type":"Playlist","Id":"abc","Name":"Next Up","Enabled":true,"UserId":"u1","MediaTypes":["Episode"],
            "ExpressionSets":[{"Expressions":[{"MemberName":"NextUnwatched","Operator":"Equal","TargetValue":"true","IncludeUnwatchedSeries":false,"UserId":"u2","FutureRuleProp":7}],"MaxItems":5,"FutureSetProp":"x"}],
            "Order":{"Name":"keep me","SortOptions":[{"SortBy":"Name","SortOrder":"Descending","AirBlockWindowDays":4}]},
            "AutoRefresh":"OnAllChanges","MaxItems":50,"PlaylistId":"123","FutureListProp":{"a":1}}""",
    )

    @Test fun `a list round-trips and keeps properties the app does not show`() {
        val json = ListDraft.from(existing).toJson()

        assertEquals("Next Up", json["Name"]!!.jsonPrimitive.content)
        assertEquals("123", json["PlaylistId"]!!.jsonPrimitive.content)
        assertTrue(json.containsKey("FutureListProp"))
        val set = json["ExpressionSets"]!!.jsonArray[0].jsonObject
        assertEquals("x", set["FutureSetProp"]!!.jsonPrimitive.content)
        assertEquals("5", set["MaxItems"]!!.jsonPrimitive.content)
        val rule = set["Expressions"]!!.jsonArray[0].jsonObject
        assertEquals("u2", rule["UserId"]!!.jsonPrimitive.content)
        assertEquals("7", rule["FutureRuleProp"]!!.jsonPrimitive.content)
        assertEquals("false", rule["IncludeUnwatchedSeries"]!!.jsonPrimitive.content)
        val order = json["Order"]!!.jsonObject
        assertEquals("keep me", order["Name"]!!.jsonPrimitive.content)
        val sort = order["SortOptions"]!!.jsonArray[0].jsonObject
        assertEquals("Descending", sort["SortOrder"]!!.jsonPrimitive.content)
        assertEquals("4", sort["AirBlockWindowDays"]!!.jsonPrimitive.content)
    }

    @Test fun `edits are applied and the owner of an existing list is not sent again`() {
        val draft = ListDraft.from(existing).copy(name = "Renamed", maxItems = "", autoRefresh = "Never")
        val json = draft.toJson()

        assertEquals("Renamed", json["Name"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, json["MaxItems"])
        assertEquals("Never", json["AutoRefresh"]!!.jsonPrimitive.content)
        assertEquals("u1", json["UserId"]!!.jsonPrimitive.content) // kept from the original, not changed
        assertFalse(draft.isNew)
    }

    @Test fun `a new list carries its owner and has no id`() {
        val draft = Templates.draft("blank", "u9").copy(name = "Movies")
        val json = draft.toJson()

        assertTrue(draft.isNew)
        assertEquals("u9", json["UserId"]!!.jsonPrimitive.content)
        assertFalse(json.containsKey("Id"))
        assertEquals(listOf("Movie"), json["MediaTypes"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun `the Next Up template matches the plugin's template`() {
        val draft = Templates.draft("next-up", "u1")
        val rules = draft.groups.single().rules

        assertEquals(setOf("Episode"), draft.mediaTypes)
        assertEquals("NextUnwatched", rules[0].field)
        assertEquals(false, rules[0].extraBool("IncludeUnwatchedSeries"))
        assertEquals("LibraryName", rules[1].field)
        assertEquals("", rules[1].value)
        assertEquals("Most Recently Watched Round Robin", draft.sorts.single().sortBy)
        assertEquals("SeriesName", draft.sorts.single().groupBy)
        assertEquals("OnAllChanges", draft.autoRefresh)
    }

    @Test fun `validation asks for the name, media types, owner and empty rule values`() {
        val ok = Templates.draft("blank", "u1").copy(name = "Movies")
        assertNull(ok.validate(catalog))

        assertTrue(ok.copy(name = " ").validate(catalog)!!.contains("name"))
        assertTrue(ok.copy(mediaTypes = emptySet()).validate(catalog)!!.contains("media type"))
        assertTrue(ok.copy(ownerId = null).validate(catalog)!!.contains("user"))

        val template = Templates.draft("next-up", "u1")
        assertTrue(template.validate(catalog)!!.contains("Library Name"))
        val filled = template.copy(groups = template.groups.map { g -> g.copy(rules = g.rules.map { if (it.field == "LibraryName") it.copy(value = "TV") else it }) })
        assertNull(filled.validate(catalog))
    }

    @Test fun `a playlist cannot hold collection-only media types, a collection can`() {
        val playlist = Templates.draft("blank", "u1").copy(name = "x", mediaTypes = setOf("Series"))
        assertTrue(playlist.validate(catalog)!!.contains("collection"))
        assertNull(playlist.copy(type = "Collection").validate(catalog))
    }

    @Test fun `a round robin sort needs a group by`() {
        val draft = Templates.draft("blank", "u1").copy(name = "x", sorts = listOf(com.feerlessleadr.embysmartlists.data.SortDraft(sortBy = "Round Robin")))
        assertTrue(draft.validate(catalog)!!.contains("group"))
    }

    @Test fun `limits must be whole numbers`() {
        val draft = Templates.draft("blank", "u1").copy(name = "x", maxItems = "12x")
        assertTrue(draft.validate(catalog)!!.contains("Maximum items"))
    }

    @Test fun `empty rule groups are not sent`() {
        val draft = Templates.draft("blank", "u1").copy(name = "x", groups = listOf(com.feerlessleadr.embysmartlists.data.GroupDraft(rules = emptyList())))
        assertEquals(JsonArray(emptyList()), draft.toJson()["ExpressionSets"])
    }

    @Test fun `video and audio fields are offered only for media that has them`() {
        fun names(types: Set<String>) = fieldsFor(catalog, types).map { it.name }.toSet()

        assertTrue("Resolution" in names(setOf("Movie")))
        assertFalse("Resolution" in names(setOf("Audio")))
        assertTrue("AudioCodec" in names(setOf("Audio")))
        assertFalse("AudioCodec" in names(setOf("Photo")))
        assertEquals(setOf("Name"), names(setOf("Collection")))
    }

    @Test fun `theme mode is read from its saved name and falls back to follow system`() {
        assertEquals(ThemeMode.Dark, ThemeMode.fromName("Dark"))
        assertEquals(ThemeMode.System, ThemeMode.fromName("nonsense"))
        assertEquals(ThemeMode.System, ThemeMode.fromName(null))
        assertEquals(listOf("Follow system", "Light", "Dark"), ThemeMode.entries.map { it.label })
        assertTrue(AppSettings::class.java.simpleName.isNotEmpty())
    }

    @Test fun `a shared playlist is recognised`() {
        val doc = SmartListDoc(
            JsonObject(
                mapOf(
                    "Type" to JsonPrimitive("Playlist"),
                    "Name" to JsonPrimitive("x"),
                    "AllUsers" to JsonPrimitive(true),
                ),
            ),
        )
        assertTrue(ListDraft.from(doc).shared)
    }
}
