package com.feerlessleadr.embysmartlists.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.util.concurrent.atomic.AtomicLong

/** Stable ids for list items in the editor, so rows keep their state while others are added or removed. */
object DraftKeys {
    private val counter = AtomicLong()
    fun next(): Long = counter.incrementAndGet()
}

/**
 * One rule. [extras] holds every other property of the rule JSON (for example `IncludeUnwatchedSeries` or `UserId`),
 * so editing a rule never drops what this app does not show.
 */
data class RuleDraft(
    val key: Long = DraftKeys.next(),
    val field: String,
    val operator: String,
    val value: String,
    val extras: JsonObject = JsonObject(emptyMap()),
) {
    fun extraBool(name: String): Boolean? = (extras[name] as? JsonPrimitive)?.booleanOrNull

    fun withExtra(name: String, value: JsonElement?): RuleDraft =
        copy(extras = JsonObject(extras.toMutableMap().also { if (value == null) it.remove(name) else it[name] = value }))
}

/** A group of rules that must all match (AND). Groups are alternatives (OR). */
data class GroupDraft(
    val key: Long = DraftKeys.next(),
    val rules: List<RuleDraft>,
    val extras: JsonObject = JsonObject(emptyMap()),
)

data class SortDraft(
    val key: Long = DraftKeys.next(),
    val sortBy: String,
    val descending: Boolean = false,
    val groupBy: String? = null,
    val within: String? = null,
    val extras: JsonObject = JsonObject(emptyMap()),
)

/** Media types only a collection can hold (mirrors the admin page). */
val COLLECTION_ONLY_MEDIA_TYPES = setOf("Series", "Season", "MusicAlbum", "LiveTvChannel", "Collection", "Playlist")

/** Media types in the order the admin page lists them, with their labels. */
val MEDIA_TYPE_LABELS = listOf(
    "Movie" to "Movie",
    "Series" to "Series (TV show)",
    "Season" to "Season (TV show)",
    "Episode" to "Episode (TV show)",
    "Audio" to "Audio (music)",
    "MusicAlbum" to "Album (music)",
    "MusicVideo" to "Music video",
    "Video" to "Video",
    "Photo" to "Photo",
    "Book" to "Book",
    "AudioBook" to "Audiobook",
    "LiveTvChannel" to "Live TV channel",
    "Collection" to "Collection",
    "Playlist" to "Playlist",
)

private val VIDEO_CAPABLE = setOf("Movie", "Episode", "MusicVideo", "Video")
private val AUDIO_CAPABLE = setOf("Movie", "Episode", "Audio", "AudioBook", "MusicVideo", "Video")
private val CONTAINER_TYPES = setOf("Collection", "Playlist")
private val CONTAINER_METADATA_FIELDS = setOf(
    "Name", "Genres", "Studios", "ProductionYear", "OfficialRating", "DateCreated",
    "DateLastRefreshed", "DateLastSaved", "ReleaseDate", "Collections",
)

/** Rule fields that make sense for the chosen media types (the admin page hides the others too). */
fun fieldsFor(catalog: Catalog, mediaTypes: Set<String>): List<FieldInfo> {
    val onlyContainers = mediaTypes.isNotEmpty() && mediaTypes.all { it in CONTAINER_TYPES }
    return catalog.fields.filter { f ->
        when {
            onlyContainers -> f.name in CONTAINER_METADATA_FIELDS
            f.category == "Video" -> mediaTypes.isEmpty() || mediaTypes.any { it in VIDEO_CAPABLE }
            f.category == "Audio" -> mediaTypes.isEmpty() || mediaTypes.any { it in AUDIO_CAPABLE }
            else -> true
        }
    }
}

/** A smart list being created or edited. */
data class ListDraft(
    /** The list JSON this draft came from (null for a new list): properties not shown here are kept on save. */
    val original: JsonObject? = null,
    val type: String = "Playlist",
    val name: String = "",
    val enabled: Boolean = true,
    /** The Emby user a new list belongs to. Fixed once the list exists. */
    val ownerId: String? = null,
    /** An existing playlist shared with several users (or everyone): its users are edited on the web page. */
    val shared: Boolean = false,
    val mediaTypes: Set<String> = emptySet(),
    val groups: List<GroupDraft> = emptyList(),
    val sorts: List<SortDraft> = listOf(SortDraft(sortBy = "Name")),
    val maxItems: String = "",
    val maxPlayTimeMinutes: String = "",
    val minItems: String = "",
    val autoRefresh: String = "Never",
) {
    val isNew: Boolean get() = original == null
    val isCollection: Boolean get() = type == "Collection"

    /** The first problem a person has to fix before saving, or null. The server validates again. */
    fun validate(catalog: Catalog?): String? {
        if (name.isBlank()) return "Give the list a name."
        if (mediaTypes.isEmpty()) return "Pick at least one media type."
        if (!isCollection) {
            val bad = mediaTypes.filter { it in COLLECTION_ONLY_MEDIA_TYPES }
            if (bad.isNotEmpty()) return "A playlist cannot hold: ${bad.joinToString(", ")}. Use a collection for those."
        }
        if (isNew && ownerId.isNullOrBlank()) return "Pick the user the list is for."
        for (group in groups) {
            for (rule in group.rules) {
                if (rule.value.isBlank()) {
                    val label = catalog?.fieldLabel(rule.field) ?: rule.field
                    return "Fill in the value for the \"$label\" rule, or remove it."
                }
            }
        }
        for (sort in sorts) {
            val info = catalog?.sorts?.firstOrNull { it.value == sort.sortBy }
            if (info?.roundRobin == true && sort.groupBy.isNullOrBlank()) return "Pick what to group by for \"${info.label}\"."
        }
        for ((label, text) in listOf("Maximum items" to maxItems, "Maximum play time" to maxPlayTimeMinutes, "Minimum items" to minItems)) {
            if (text.isNotBlank() && (text.toIntOrNull() ?: -1) < 0) return "$label must be a whole number (or empty)."
        }
        return null
    }

    /** The JSON to send: the original list with the edited properties applied. */
    fun toJson(): JsonObject {
        val result = (original?.toMutableMap() ?: mutableMapOf())
        result["Type"] = JsonPrimitive(type)
        result["Name"] = JsonPrimitive(name.trim())
        result["Enabled"] = JsonPrimitive(enabled)
        result["MediaTypes"] = JsonArray(mediaTypes.sortedBy { t -> MEDIA_TYPE_LABELS.indexOfFirst { it.first == t }.let { if (it < 0) 99 else it } }.map { JsonPrimitive(it) })
        result["ExpressionSets"] = JsonArray(
            groups.filter { it.rules.isNotEmpty() }.map { group ->
                JsonObject(
                    group.extras.toMutableMap().also { it["Expressions"] = JsonArray(group.rules.map(::ruleJson)) },
                )
            },
        )
        val order = (original?.get("Order") as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        order["SortOptions"] = JsonArray(sorts.map(::sortJson))
        result["Order"] = JsonObject(order)
        result["MaxItems"] = maxItems.trim().toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull
        result["MaxPlayTimeMinutes"] = maxPlayTimeMinutes.trim().toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull
        result["MinItems"] = minItems.trim().toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull
        result["AutoRefresh"] = JsonPrimitive(autoRefresh)
        if (isNew && !ownerId.isNullOrBlank()) result["UserId"] = JsonPrimitive(ownerId)
        return JsonObject(result)
    }

    private fun ruleJson(rule: RuleDraft): JsonObject = JsonObject(
        rule.extras.toMutableMap().also {
            it["MemberName"] = JsonPrimitive(rule.field)
            it["Operator"] = JsonPrimitive(rule.operator)
            it["TargetValue"] = JsonPrimitive(rule.value)
        },
    )

    private fun sortJson(sort: SortDraft): JsonObject = JsonObject(
        sort.extras.toMutableMap().also {
            it["SortBy"] = JsonPrimitive(sort.sortBy)
            it["SortOrder"] = JsonPrimitive(if (sort.descending) "Descending" else "Ascending")
            if (sort.groupBy != null) it["GroupByField"] = JsonPrimitive(sort.groupBy) else it.remove("GroupByField")
            if (sort.within != null) it["WithinGroupOrder"] = JsonPrimitive(sort.within) else it.remove("WithinGroupOrder")
        },
    )

    companion object {
        /** Reads an existing list into an editable draft. */
        fun from(doc: SmartListDoc): ListDraft {
            val json = doc.json
            val userPlaylists = (json["UserPlaylists"] as? JsonArray).orEmpty()
            val shared = (json["AllUsers"] as? JsonPrimitive)?.booleanOrNull == true ||
                (json["Public"] as? JsonPrimitive)?.booleanOrNull == true ||
                userPlaylists.size > 1
            val owner = doc.userId ?: userPlaylists.firstNotNullOfOrNull { ((it as? JsonObject)?.get("UserId") as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }

            val groups = (json["ExpressionSets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { set ->
                GroupDraft(
                    rules = (set["Expressions"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map(::ruleFrom),
                    extras = JsonObject(set.filterKeys { it != "Expressions" }),
                )
            }
            val sorts = doc.sortOptions.map { o ->
                SortDraft(
                    sortBy = o.stringAt("SortBy").orEmpty(),
                    descending = o.stringAt("SortOrder") == "Descending",
                    groupBy = o.stringAt("GroupByField"),
                    within = o.stringAt("WithinGroupOrder"),
                    extras = JsonObject(o.filterKeys { it !in setOf("SortBy", "SortOrder", "GroupByField", "WithinGroupOrder") }),
                )
            }
            return ListDraft(
                original = json,
                type = doc.type,
                name = doc.name,
                enabled = doc.enabled,
                ownerId = owner,
                shared = shared,
                mediaTypes = doc.mediaTypes.toSet(),
                groups = groups,
                sorts = sorts.ifEmpty { listOf(SortDraft(sortBy = "NoOrder")) },
                maxItems = doc.maxItems?.toString().orEmpty(),
                maxPlayTimeMinutes = doc.maxPlayTimeMinutes?.toString().orEmpty(),
                minItems = doc.minItems?.toString().orEmpty(),
                autoRefresh = doc.autoRefresh,
            )
        }

        private fun ruleFrom(json: JsonObject) = RuleDraft(
            field = json.ruleField(),
            operator = json.ruleOperator(),
            value = json.ruleValue(),
            extras = JsonObject(json.filterKeys { it !in setOf("MemberName", "Operator", "TargetValue") }),
        )
    }
}
