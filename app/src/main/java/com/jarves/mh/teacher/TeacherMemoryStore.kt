package com.jarves.mh.teacher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.ArrayList

class TeacherMemoryStore(context: Context) {
    private val baseDir = File(context.filesDir, "teacher").apply { mkdirs() }
    private val profileFile = File(baseDir, "profile.json")
    private val conceptsFile = File(baseDir, "concepts.json")
    private val prereqsFile = File(baseDir, "prerequisites.json")
    private val reviewsFile = File(baseDir, "reviews.json")
    private val scratchpadFile = File(baseDir, "scratchpad.json")
    private val pluginsFile = File(baseDir, "plugins.json")

    @Volatile
    private var cachedProfile: LearnerProfile? = null
    @Volatile
    private var cachedConcepts: ArrayList<Concept>? = null
    @Volatile
    private var cachedPrereqs: ArrayList<ConceptPrerequisite>? = null
    @Volatile
    private var cachedReviews: ArrayList<FsrsCard>? = null
    @Volatile
    private var cachedScratchpad: ArrayList<ScratchpadEntry>? = null
    @Volatile
    private var cachedPlugins: ArrayList<AppPluginDefinition>? = null


    @Synchronized
    fun loadProfile(): LearnerProfile {
        cachedProfile?.let { return it }
        if (!profileFile.isFile) return LearnerProfile().also { cachedProfile = it }

        return runCatching {
            val text = profileFile.readText()
            if (text.isBlank()) return@runCatching LearnerProfile()
            val obj = JSONObject(text)
            val preferredStyle = runCatching {
                ExplanationStyle.valueOf(obj.optString("preferredStyle", ExplanationStyle.SOCRATIC.name))
            }.getOrDefault(ExplanationStyle.SOCRATIC)

            val subjectsArr = obj.optJSONArray("activeSubjects")
            val subLen = subjectsArr?.length() ?: 0
            val subjects = if (subLen > 0) {
                val list = ArrayList<String>(subLen)
                for (i in 0 until subLen) {
                    subjectsArr?.optString(i)?.let { list.add(it) }
                }
                list
            } else {
                listOf("Computer Science", "Mathematics")
            }

            val goalsArr = obj.optJSONArray("goals")
            val goalsLen = goalsArr?.length() ?: 0
            val goals = if (goalsLen > 0) {
                val list = ArrayList<String>(goalsLen)
                for (i in 0 until goalsLen) {
                    goalsArr?.optString(i)?.let { list.add(it) }
                }
                list
            } else {
                emptyList()
            }

            LearnerProfile(
                id = obj.optString("id", "primary_learner"),
                name = obj.optString("name", "Learner"),
                preferredStyle = preferredStyle,
                activeSubjects = subjects,
                goals = goals,
                notes = obj.optString("notes", ""),
                totalSessions = obj.optInt("totalSessions", 0),
                totalExercises = obj.optInt("totalExercises", 0),
                totalMasteredConcepts = obj.optInt("totalMasteredConcepts", 0),
            )
        }.getOrDefault(LearnerProfile()).also { cachedProfile = it }
    }

    @Synchronized
    fun saveProfile(profile: LearnerProfile) {
        cachedProfile = profile
        val obj = JSONObject().apply {
            put("id", profile.id)
            put("name", profile.name)
            put("preferredStyle", profile.preferredStyle.name)
            put("activeSubjects", JSONArray(profile.activeSubjects))
            put("goals", JSONArray(profile.goals))
            put("notes", profile.notes)
            put("totalSessions", profile.totalSessions)
            put("totalExercises", profile.totalExercises)
            put("totalMasteredConcepts", profile.totalMasteredConcepts)
        }
        writeAtomic(profileFile, obj.toString())
    }

    @Synchronized
    fun loadConcepts(): List<Concept> {
        cachedConcepts?.let { return ArrayList(it) }
        if (!conceptsFile.isFile) {
            val empty = ArrayList<Concept>()
            cachedConcepts = empty
            return empty
        }

        val list = runCatching {
            val text = conceptsFile.readText()
            if (text.isBlank()) return@runCatching ArrayList<Concept>()
            val arr = JSONArray(text)
            val len = arr.length()
            val result = ArrayList<Concept>(len)
            for (i in 0 until len) {
                val obj = arr.getJSONObject(i)
                val miscArr = obj.optJSONArray("misconceptions")
                val miscLen = miscArr?.length() ?: 0
                val misconceptions = if (miscLen > 0) {
                    val mList = ArrayList<String>(miscLen)
                    for (j in 0 until miscLen) {
                        miscArr?.optString(j)?.let { mList.add(it) }
                    }
                    mList
                } else {
                    emptyList()
                }

                result.add(
                    Concept(
                        id = obj.getString("id"),
                        subject = obj.optString("subject", "General"),
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        mastery = obj.optDouble("mastery", 0.0).toFloat(),
                        pKnown = obj.optDouble("pKnown", 0.1).toFloat(),
                        misconceptions = misconceptions,
                        attempts = obj.optInt("attempts", 0),
                        correctCount = obj.optInt("correctCount", 0),
                        lastPracticedMillis = obj.optLong("lastPracticedMillis", 0L),
                    ),
                )
            }
            result
        }.getOrDefault(ArrayList())

        cachedConcepts = list
        return ArrayList(list)
    }

    @Synchronized
    fun saveConcepts(concepts: List<Concept>) {
        cachedConcepts = ArrayList(concepts)
        val arr = JSONArray()
        for (c in concepts) {
            val obj = JSONObject().apply {
                put("id", c.id)
                put("subject", c.subject)
                put("name", c.name)
                if (c.description.isNotEmpty()) put("description", c.description)
                put("mastery", c.mastery)
                put("pKnown", c.pKnown)
                if (c.misconceptions.isNotEmpty()) put("misconceptions", JSONArray(c.misconceptions))
                put("attempts", c.attempts)
                put("correctCount", c.correctCount)
                put("lastPracticedMillis", c.lastPracticedMillis)
            }
            arr.put(obj)
        }
        writeAtomic(conceptsFile, arr.toString())
    }

    @Synchronized
    fun saveConcept(concept: Concept) {
        val all = (cachedConcepts ?: ArrayList(loadConcepts())).let { ArrayList(it) }
        val index = all.indexOfFirst { it.id == concept.id }
        if (index >= 0) {
            all[index] = concept
        } else {
            all.add(concept)
        }
        saveConcepts(all)
    }

    @Synchronized
    fun loadPrerequisites(): List<ConceptPrerequisite> {
        cachedPrereqs?.let { return ArrayList(it) }
        if (!prereqsFile.isFile) {
            val empty = ArrayList<ConceptPrerequisite>()
            cachedPrereqs = empty
            return empty
        }

        val list = runCatching {
            val text = prereqsFile.readText()
            if (text.isBlank()) return@runCatching ArrayList<ConceptPrerequisite>()
            val arr = JSONArray(text)
            val len = arr.length()
            val result = ArrayList<ConceptPrerequisite>(len)
            for (i in 0 until len) {
                val obj = arr.getJSONObject(i)
                result.add(
                    ConceptPrerequisite(
                        conceptId = obj.getString("conceptId"),
                        prerequisiteId = obj.getString("prerequisiteId"),
                        isStrict = obj.optBoolean("isStrict", true),
                    ),
                )
            }
            result
        }.getOrDefault(ArrayList())

        cachedPrereqs = list
        return ArrayList(list)
    }

    @Synchronized
    fun savePrerequisites(prereqs: List<ConceptPrerequisite>) {
        cachedPrereqs = ArrayList(prereqs)
        val arr = JSONArray()
        for (p in prereqs) {
            val obj = JSONObject().apply {
                put("conceptId", p.conceptId)
                put("prerequisiteId", p.prerequisiteId)
                put("isStrict", p.isStrict)
            }
            arr.put(obj)
        }
        writeAtomic(prereqsFile, arr.toString())
    }

    @Synchronized
    fun loadReviews(): List<FsrsCard> {
        cachedReviews?.let { return ArrayList(it) }
        if (!reviewsFile.isFile) {
            val empty = ArrayList<FsrsCard>()
            cachedReviews = empty
            return empty
        }

        val list = runCatching {
            val text = reviewsFile.readText()
            if (text.isBlank()) return@runCatching ArrayList<FsrsCard>()
            val arr = JSONArray(text)
            val len = arr.length()
            val result = ArrayList<FsrsCard>(len)
            for (i in 0 until len) {
                val obj = arr.getJSONObject(i)
                val stateStr = obj.optString("state", CardState.NEW.name)
                val state = runCatching { CardState.valueOf(stateStr) }.getOrDefault(CardState.NEW)

                result.add(
                    FsrsCard(
                        conceptId = obj.getString("conceptId"),
                        stability = obj.optDouble("stability", 2.0).toFloat(),
                        difficulty = obj.optDouble("difficulty", 5.0).toFloat(),
                        dueMillis = obj.optLong("dueMillis", System.currentTimeMillis()),
                        lastReviewMillis = obj.optLong("lastReviewMillis", 0L),
                        reps = obj.optInt("reps", 0),
                        lapses = obj.optInt("lapses", 0),
                        state = state,
                    ),
                )
            }
            result
        }.getOrDefault(ArrayList())

        cachedReviews = list
        return ArrayList(list)
    }

    @Synchronized
    fun saveReviews(reviews: List<FsrsCard>) {
        cachedReviews = ArrayList(reviews)
        val arr = JSONArray()
        for (r in reviews) {
            val obj = JSONObject().apply {
                put("conceptId", r.conceptId)
                put("stability", r.stability)
                put("difficulty", r.difficulty)
                put("dueMillis", r.dueMillis)
                put("lastReviewMillis", r.lastReviewMillis)
                put("reps", r.reps)
                put("lapses", r.lapses)
                put("state", r.state.name)
            }
            arr.put(obj)
        }
        writeAtomic(reviewsFile, arr.toString())
    }

    @Synchronized
    fun loadScratchpad(): List<ScratchpadEntry> {
        cachedScratchpad?.let { return ArrayList(it) }
        if (!scratchpadFile.isFile) {
            val empty = ArrayList<ScratchpadEntry>()
            cachedScratchpad = empty
            return empty
        }

        val list = runCatching {
            val text = scratchpadFile.readText()
            if (text.isBlank()) return@runCatching ArrayList<ScratchpadEntry>()
            val arr = JSONArray(text)
            val len = arr.length()
            val result = ArrayList<ScratchpadEntry>(len)
            for (i in 0 until len) {
                val obj = arr.getJSONObject(i)
                val type = runCatching { ScratchpadType.valueOf(obj.optString("type", ScratchpadType.NOTE.name)) }
                    .getOrDefault(ScratchpadType.NOTE)

                result.add(
                    ScratchpadEntry(
                        id = obj.getString("id"),
                        taskId = obj.optString("taskId", "default"),
                        title = obj.getString("title"),
                        content = obj.getString("content"),
                        type = type,
                        isPinned = obj.optBoolean("isPinned", false),
                        createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
                    ),
                )
            }
            result.sortByDescending { it.isPinned }
            result
        }.getOrDefault(ArrayList())

        cachedScratchpad = list
        return ArrayList(list)
    }

    @Synchronized
    fun saveScratchpad(entries: List<ScratchpadEntry>) {
        cachedScratchpad = ArrayList(entries)
        val arr = JSONArray()
        for (e in entries) {
            val obj = JSONObject().apply {
                put("id", e.id)
                put("taskId", e.taskId)
                put("title", e.title)
                put("content", e.content)
                put("type", e.type.name)
                put("isPinned", e.isPinned)
                put("createdAtMillis", e.createdAtMillis)
            }
            arr.put(obj)
        }
        writeAtomic(scratchpadFile, arr.toString())
    }

    @Synchronized
    fun addScratchpadEntry(entry: ScratchpadEntry) {
        val entries = (cachedScratchpad ?: ArrayList(loadScratchpad())).let { ArrayList(it) }
        val index = entries.indexOfFirst { it.id == entry.id }
        if (index >= 0) {
            entries[index] = entry
        } else {
            entries.add(0, entry)
        }
        saveScratchpad(entries)
    }

    @Synchronized
    fun deleteScratchpadEntry(id: String) {
        val entries = (cachedScratchpad ?: ArrayList(loadScratchpad())).let { ArrayList(it) }
        val index = entries.indexOfFirst { it.id == id }
        if (index >= 0) {
            entries.removeAt(index)
            saveScratchpad(entries)
        }
    }

    @Synchronized
    fun clearScratchpad() {
        val entries = cachedScratchpad ?: ArrayList(loadScratchpad())
        val pinned = entries.filter { it.isPinned }
        saveScratchpad(pinned)
    }

    @Synchronized
    fun loadPlugins(): List<AppPluginDefinition> {
        cachedPlugins?.let { return ArrayList(it) }
        if (!pluginsFile.isFile) {
            val empty = ArrayList<AppPluginDefinition>()
            cachedPlugins = empty
            return empty
        }

        val list = runCatching {
            val text = pluginsFile.readText()
            if (text.isBlank()) return@runCatching ArrayList<AppPluginDefinition>()
            val arr = JSONArray(text)
            val len = arr.length()
            val result = ArrayList<AppPluginDefinition>(len)
            for (i in 0 until len) {
                val obj = arr.getJSONObject(i)
                val capsArr = obj.optJSONArray("capabilities")
                val capsLen = capsArr?.length() ?: 0
                val capabilities = if (capsLen > 0) {
                    val cSet = mutableSetOf<AppPluginCapability>()
                    for (j in 0 until capsLen) {
                        val capStr = capsArr?.optString(j) ?: continue
                        runCatching { AppPluginCapability.valueOf(capStr) }.getOrNull()?.let { cSet.add(it) }
                    }
                    cSet
                } else {
                    setOf(AppPluginCapability.GENERAL_INTENT)
                }

                result.add(
                    AppPluginDefinition(
                        id = obj.getString("id"),
                        packageName = obj.getString("packageName"),
                        displayName = obj.getString("displayName"),
                        teachingRole = obj.optString("teachingRole", "Interactive Practice Tool"),
                        targetSubject = obj.optString("targetSubject", "General"),
                        launchAction = obj.optString("launchAction", "android.intent.action.MAIN"),
                        deepLinkTemplate = obj.optString("deepLinkTemplate", "").takeIf { it.isNotBlank() },
                        isEnabled = obj.optBoolean("isEnabled", true),
                        isAutoInvokeAllowed = obj.optBoolean("isAutoInvokeAllowed", false),
                        capabilities = capabilities,
                        notes = obj.optString("notes", ""),
                        installedVersion = obj.optString("installedVersion", "").takeIf { it.isNotBlank() },
                        isInstalled = obj.optBoolean("isInstalled", true),
                        connectedAtMillis = obj.optLong("connectedAtMillis", System.currentTimeMillis()),
                    ),
                )
            }
            result
        }.getOrDefault(ArrayList())

        cachedPlugins = list
        return ArrayList(list)
    }

    @Synchronized
    fun savePlugins(plugins: List<AppPluginDefinition>) {
        cachedPlugins = ArrayList(plugins)
        val arr = JSONArray()
        for (p in plugins) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("packageName", p.packageName)
                put("displayName", p.displayName)
                put("teachingRole", p.teachingRole)
                put("targetSubject", p.targetSubject)
                put("launchAction", p.launchAction)
                if (!p.deepLinkTemplate.isNullOrBlank()) put("deepLinkTemplate", p.deepLinkTemplate)
                put("isEnabled", p.isEnabled)
                put("isAutoInvokeAllowed", p.isAutoInvokeAllowed)
                val capsArr = JSONArray()
                for (cap in p.capabilities) {
                    capsArr.put(cap.name)
                }
                put("capabilities", capsArr)
                put("notes", p.notes)
                if (!p.installedVersion.isNullOrBlank()) put("installedVersion", p.installedVersion)
                put("isInstalled", p.isInstalled)
                put("connectedAtMillis", p.connectedAtMillis)
            }
            arr.put(obj)
        }
        writeAtomic(pluginsFile, arr.toString())
    }

    @Synchronized
    fun savePlugin(plugin: AppPluginDefinition) {
        val all = (cachedPlugins ?: ArrayList(loadPlugins())).let { ArrayList(it) }
        val index = all.indexOfFirst { it.id == plugin.id }
        if (index >= 0) {
            all[index] = plugin
        } else {
            all.add(plugin)
        }
        savePlugins(all)
    }

    @Synchronized
    fun deletePlugin(id: String): Boolean {
        val all = (cachedPlugins ?: ArrayList(loadPlugins())).let { ArrayList(it) }
        val index = all.indexOfFirst { it.id == id }
        if (index >= 0) {
            all.removeAt(index)
            savePlugins(all)
            return true
        }
        return false
    }

    @Synchronized
    fun resetAllMemory() {
        baseDir.deleteRecursively()
        baseDir.mkdirs()
        cachedProfile = null
        cachedConcepts = null
        cachedPrereqs = null
        cachedReviews = null
        cachedScratchpad = null
        cachedPlugins = null
    }

    private fun writeAtomic(target: File, text: String) {
        val temp = File(target.parentFile, ".${target.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
        }
        temp.delete()
    }
}
