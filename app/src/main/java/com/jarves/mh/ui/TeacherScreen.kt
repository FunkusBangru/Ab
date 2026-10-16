package com.jarves.mh.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarves.mh.teacher.*
import com.jarves.mh.ui.theme.PocketGreen
import com.jarves.mh.ui.theme.PocketOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherScreen(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val memoryStore = remember { TeacherMemoryStore(context) }
    val pedagogicalEngine = remember { TeacherPedagogicalEngine(memoryStore) }
    val gatekeeper = remember { AutonomyGatekeeper() }
    val skillManager = remember { SkillManager(File(context.filesDir, "skills")) }
    val pluginManager = remember { AppPluginManager(memoryStore) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = remember { listOf("Teacher", "App Plugins", "Scratchpad", "Skills & MCP", "Autonomy & System") }

    var learnerProfile by remember { mutableStateOf(memoryStore.loadProfile()) }
    var concepts by remember { mutableStateOf(memoryStore.loadConcepts()) }
    var scratchpadEntries by remember { mutableStateOf(memoryStore.loadScratchpad()) }
    var skills by remember { mutableStateOf(skillManager.listSkills()) }
    var plugins by remember { mutableStateOf(pluginManager.listConnectedPlugins()) }
    var activeAutonomyMode by remember { mutableStateOf(gatekeeper.currentMode) }
    var shizukuStatus by remember { mutableStateOf(ShizukuSupport.checkStatus(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Teacher & Personal Agent", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Self-Evolving Cognitive Learning System", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        concepts = memoryStore.loadConcepts()
                        scratchpadEntries = memoryStore.loadScratchpad()
                        skills = skillManager.listSkills()
                        plugins = pluginManager.listConnectedPlugins()
                        shizukuStatus = ShizukuSupport.checkStatus(context)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                    )
                }
            }

            when (selectedTab) {
                0 -> TeacherTabContent(
                    profile = learnerProfile,
                    concepts = concepts,
                    engine = pedagogicalEngine,
                    pluginManager = pluginManager,
                    onProfileUpdated = { updated ->
                        memoryStore.saveProfile(updated)
                        learnerProfile = updated
                    },
                    onConceptUpdated = {
                        concepts = memoryStore.loadConcepts()
                    },
                )
                1 -> AppPluginsTabContent(
                    plugins = plugins,
                    pluginManager = pluginManager,
                    onPluginUpdated = {
                        plugins = pluginManager.listConnectedPlugins()
                    },
                )
                2 -> ScratchpadTabContent(
                    entries = scratchpadEntries,
                    onAddEntry = { entry ->
                        memoryStore.addScratchpadEntry(entry)
                        scratchpadEntries = memoryStore.loadScratchpad()
                    },
                    onDeleteEntry = { id ->
                        memoryStore.deleteScratchpadEntry(id)
                        scratchpadEntries = memoryStore.loadScratchpad()
                    },
                    onClear = {
                        memoryStore.clearScratchpad()
                        scratchpadEntries = memoryStore.loadScratchpad()
                    },
                )
                3 -> SkillsTabContent(
                    skills = skills,
                    onAddSkill = { skill, script, test ->
                        skillManager.saveSkill(skill, script, test)
                        skills = skillManager.listSkills()
                    },
                    onToggleSkill = { id, enabled ->
                        skillManager.toggleSkill(id, enabled)
                        skills = skillManager.listSkills()
                    },
                    onDeleteSkill = { id ->
                        skillManager.deleteSkill(id)
                        skills = skillManager.listSkills()
                    },
                )
                4 -> AutonomyTabContent(
                    activeMode = activeAutonomyMode,
                    shizukuStatus = shizukuStatus,
                    onModeChanged = { mode ->
                        gatekeeper.currentMode = mode
                        activeAutonomyMode = mode
                    },
                )
            }
        }
    }
}

@Composable
private fun TeacherTabContent(
    profile: LearnerProfile,
    concepts: List<Concept>,
    engine: TeacherPedagogicalEngine,
    pluginManager: AppPluginManager,
    onProfileUpdated: (LearnerProfile) -> Unit,
    onConceptUpdated: () -> Unit,
) {
    val context = LocalContext.current
    var showStylePicker by remember { mutableStateOf(false) }
    val recommended = remember(concepts) { engine.getNextRecommendedConcept() }
    val suggestedPlugins = remember(recommended) {
        if (recommended != null) pluginManager.suggestPluginsForConcept(recommended) else emptyList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "learner_profile_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(PocketOrange.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.School, contentDescription = null, tint = PocketOrange, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Style: ${profile.preferredStyle.label}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { showStylePicker = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Text("Change Style", fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        MetricColumn("Mastered", "${profile.totalMasteredConcepts}")
                        MetricColumn("Practiced", "${profile.totalExercises}")
                        MetricColumn("Active Concepts", "${concepts.size}")
                    }
                }
            }
        }

        item(key = "zpd_recommendation_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, PocketOrange.copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = PocketOrange, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Zone of Proximal Development (ZPD)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    if (recommended != null) {
                        Text(recommended.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Subject: ${recommended.subject}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(
                            progress = { recommended.mastery },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = PocketOrange,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Text(
                            "Bayesian Knowledge Probability P(L): ${(recommended.pKnown * 100).toInt()}% · Mastery: ${(recommended.mastery * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    engine.recordExerciseResult(recommended.id, isCorrect = true, rating = FsrsRating.GOOD)
                                    onConceptUpdated()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PocketGreen),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Mark Understood", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    engine.recordExerciseResult(recommended.id, isCorrect = false, rating = FsrsRating.AGAIN, detectedMisconception = "Requires guided scaffolding")
                                    onConceptUpdated()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Struggled / Hint Needed", fontSize = 12.sp)
                            }
                        }

                        if (suggestedPlugins.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Text("Interactive App Practice:", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = PocketOrange)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                suggestedPlugins.take(2).forEach { p ->
                                    OutlinedButton(
                                        onClick = {
                                            pluginManager.launchPlugin(
                                                context,
                                                p,
                                                payloadText = "Practice ${recommended.name} in ${recommended.subject}",
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Open in ${p.displayName}", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        Text("No concepts registered yet. Start a lesson to track your knowledge tree.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item(key = "interactive_lesson_card") {
            InteractiveLessonCard(
                engine = engine,
                onConceptUpdated = onConceptUpdated,
            )
        }

        item(key = "knowledge_graph_header") {
            Text("Tracked Concept Knowledge Graph", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        if (concepts.isEmpty()) {
            item(key = "empty_concepts_notice") {
                Text("Concepts learned during sessions will be tracked here with BKT and FSRS schedules.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(concepts, key = { it.id }) { c ->
                ConceptRow(concept = c)
            }
        }
    }

    if (showStylePicker) {
        AlertDialog(
            onDismissRequest = { showStylePicker = false },
            title = { Text("Select Teaching Style") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExplanationStyle.entries.forEach { style ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onProfileUpdated(profile.copy(preferredStyle = style))
                                    showStylePicker = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = profile.preferredStyle == style,
                                onClick = {
                                    onProfileUpdated(profile.copy(preferredStyle = style))
                                    showStylePicker = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(style.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(style.promptInstruction.take(65) + "...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStylePicker = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun InteractiveLessonCard(
    engine: TeacherPedagogicalEngine,
    onConceptUpdated: () -> Unit,
) {
    val lesson = remember { InteractiveLessonEngine.createCompoundInterestLesson() }
    val lessonEngine = remember {
        InteractiveLessonEngine().apply {
            initializeFromPlan(lesson)
        }
    }

    var annualRate by remember { mutableFloatStateOf(5f) }
    var principal by remember { mutableFloatStateOf(1000f) }
    var years by remember { mutableFloatStateOf(10f) }

    val simulationResult = remember(annualRate, principal, years) {
        lessonEngine.setVariable("annual_rate", annualRate.toDouble())
        lessonEngine.setVariable("principal", principal.toDouble())
        lessonEngine.setVariable("years", years.toDouble())
        lessonEngine.computeSimulation("compound_interest_simulation")
    }

    var selectedAssessmentOption by remember { mutableIntStateOf(-1) }
    var submissionResult by remember { mutableStateOf<AssessmentSubmissionResult?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, PocketOrange.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(PocketOrange.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PocketOrange, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(lesson.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Interactive Lesson · Schema v${lesson.version}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            lesson.blocks.filterIsInstance<UiBlock.ExplanationBlock>().firstOrNull()?.let { exp ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        exp.content,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Interactive Model Controls (Local Reactive Recomputation):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)

                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Annual interest rate (r):", fontSize = 11.sp)
                        Text("${String.format("%.1f", annualRate)}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PocketOrange)
                    }
                    Slider(
                        value = annualRate,
                        onValueChange = { annualRate = it },
                        valueRange = 0f..15f,
                        steps = 29,
                    )
                }

                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Initial Principal (P):", fontSize = 11.sp)
                        Text("$${principal.toInt()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PocketOrange)
                    }
                    Slider(
                        value = principal,
                        onValueChange = { principal = it },
                        valueRange = 100f..5000f,
                        steps = 48,
                    )
                }

                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Duration (t):", fontSize = 11.sp)
                        Text("${years.toInt()} years", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PocketOrange)
                    }
                    Slider(
                        value = years,
                        onValueChange = { years = it },
                        valueRange = 1f..30f,
                        steps = 28,
                    )
                }
            }

            val finalAmount = simulationResult.summary["final_amount"] as? Float ?: 0f
            val totalInterest = simulationResult.summary["total_interest"] as? Float ?: 0f
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Formula A = P(1 + r/100)^t", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$${String.format("%.2f", finalAmount)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PocketGreen)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Interest Earned", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("+$${String.format("%.2f", totalInterest)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PocketOrange)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Growth Curve (Updated Instantly):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                val points = simulationResult.points
                val orangeColor = PocketOrange
                val greenColor = PocketGreen
                val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    if (points.isEmpty()) return@Canvas
                    val w = size.width
                    val h = size.height

                    val maxY = points.maxOfOrNull { it.y }?.coerceAtLeast(100f) ?: 1000f
                    val maxX = points.maxOfOrNull { it.x }?.coerceAtLeast(1f) ?: 10f

                    drawLine(
                        color = gridColor,
                        start = Offset(0f, h),
                        end = Offset(w, h),
                        strokeWidth = 1f,
                    )

                    val path = Path()
                    points.forEachIndexed { index, pt ->
                        val px = (pt.x / maxX) * w
                        val py = h - ((pt.y / maxY) * (h - 10f))
                        if (index == 0) {
                            path.moveTo(px, py)
                        } else {
                            path.lineTo(px, py)
                        }
                    }

                    drawPath(
                        path = path,
                        color = orangeColor,
                        style = Stroke(width = 3.dp.toPx()),
                    )

                    val lastPt = points.last()
                    val endX = (lastPt.x / maxX) * w
                    val endY = h - ((lastPt.y / maxY) * (h - 10f))
                    drawCircle(
                        color = greenColor,
                        radius = 4.dp.toPx(),
                        center = Offset(endX, endY),
                    )
                }
            }

            lesson.blocks.filterIsInstance<UiBlock.AssessmentBlock>().firstOrNull()?.let { assess ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Conceptual Check:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(assess.prompt, fontSize = 11.5.sp)

                    assess.options.forEachIndexed { idx, opt ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedAssessmentOption = idx }
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(
                                selected = selectedAssessmentOption == idx,
                                onClick = { selectedAssessmentOption = idx },
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(opt, fontSize = 11.sp)
                        }
                    }

                    Button(
                        onClick = {
                            if (selectedAssessmentOption >= 0) {
                                val result = lessonEngine.assessAnswer(
                                    plan = lesson,
                                    assessmentId = assess.id,
                                    selectedOptionIndex = selectedAssessmentOption,
                                    pedagogicalEngine = engine,
                                )
                                submissionResult = result
                                onConceptUpdated()
                            }
                        },
                        enabled = selectedAssessmentOption >= 0,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PocketOrange),
                    ) {
                        Text("Submit Assessment", fontSize = 12.sp)
                    }

                    if (submissionResult != null) {
                        val res = submissionResult!!
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (res.isCorrect) PocketGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    res.feedback,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (res.isCorrect) PocketGreen else MaterialTheme.colorScheme.error,
                                )
                                if (res.updatedConcept != null) {
                                    Text(
                                        "Teaching Decision: Mastery ${(res.updatedConcept.mastery * 100).toInt()}% · BKT P(L)=${String.format("%.2f", res.updatedConcept.pKnown)}",
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                                if (res.nextRecommendedConcept != null) {
                                    Text(
                                        "Next Pedagogical Step: ${res.nextRecommendedConcept.name} (${res.nextRecommendedConcept.subject})",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = PocketOrange,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPluginsTabContent(
    plugins: List<AppPluginDefinition>,
    pluginManager: AppPluginManager,
    onPluginUpdated: () -> Unit,
) {
    val context = LocalContext.current
    var showConnectDialog by remember { mutableStateOf(false) }
    var activeTestPlugin by remember { mutableStateOf<AppPluginDefinition?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Device Application Plugins", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Connect local Android apps for code execution, graphing, and drills",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { showConnectDialog = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Connect App", fontSize = 12.sp)
            }
        }

        if (statusMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        statusMessage ?: "",
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    IconButton(onClick = { statusMessage = null }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        if (plugins.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = PocketOrange,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        "No Device Applications Connected",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    Text(
                        "Link installed apps on this device (Termux, Pydroid, GeoGebra, Anki, Wikipedia, etc.) to use their capabilities for interactive learning.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showConnectDialog = true }) {
                        Text("Browse Device Apps", fontSize = 12.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(plugins, key = { it.id }) { plugin ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(PocketOrange.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Android,
                                        contentDescription = null,
                                        tint = PocketOrange,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(plugin.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        plugin.packageName,
                                        fontSize = 10.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = plugin.isEnabled,
                                    onCheckedChange = {
                                        pluginManager.togglePlugin(plugin.id, it)
                                        onPluginUpdated()
                                    },
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "Role: ${plugin.teachingRole}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PocketOrange,
                                )
                                Text(
                                    "Subject: ${plugin.targetSubject}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (plugin.capabilities.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    plugin.capabilities.forEach { cap ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(4.dp),
                                        ) {
                                            Text(
                                                cap.label,
                                                fontSize = 9.5.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontWeight = FontWeight.Medium,
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = {
                                        val res = pluginManager.launchPlugin(context, plugin)
                                        statusMessage = res.message
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Launch App", fontSize = 11.5.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        activeTestPlugin = plugin
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Send Practice", fontSize = 11.5.sp)
                                }

                                IconButton(
                                    onClick = {
                                        pluginManager.disconnectApp(plugin.id)
                                        onPluginUpdated()
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Disconnect", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConnectDialog) {
        ConnectAppDialog(
            pluginManager = pluginManager,
            onDismiss = { showConnectDialog = false },
            onAppConnected = {
                showConnectDialog = false
                onPluginUpdated()
            },
        )
    }

    if (activeTestPlugin != null) {
        SendPayloadDialog(
            plugin = activeTestPlugin!!,
            onDismiss = { activeTestPlugin = null },
            onSend = { payload ->
                val res = pluginManager.sendTeachingPayload(context, activeTestPlugin!!, payload)
                statusMessage = res.message
                activeTestPlugin = null
            },
        )
    }
}

@Composable
private fun ConnectAppDialog(
    pluginManager: AppPluginManager,
    onDismiss: () -> Unit,
    onAppConnected: () -> Unit,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var appsList by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(searchQuery) {
        isLoading = true
        appsList = withContext(Dispatchers.IO) {
            pluginManager.scanInstalledApps(context, searchQuery = searchQuery, includeSystemApps = false)
        }
        isLoading = false
    }

    if (selectedApp == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Connect Installed Device App", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name, package, or subject...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    if (isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = PocketOrange)
                        }
                    } else if (appsList.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No matching apps found.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(appsList, key = { it.packageName }) { app ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedApp = app },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(PocketOrange.copy(alpha = 0.15f), CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Default.Android, contentDescription = null, tint = PocketOrange, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(app.appName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            Text(app.suggestedRole ?: app.packageName, fontSize = 11.sp, color = PocketOrange)
                                        }
                                        Icon(Icons.Default.Add, contentDescription = "Select", tint = PocketGreen, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            },
        )
    } else {
        val app = selectedApp!!
        var role by remember { mutableStateOf(app.suggestedRole ?: "Practice Environment") }
        var subject by remember { mutableStateOf(app.suggestedSubject ?: "General") }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { selectedApp = null },
            title = { Text("Configure Plugin: ${app.appName}", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Package: ${app.packageName}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = role,
                        onValueChange = { role = it },
                        label = { Text("Teaching Role", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Target Subject", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Pedagogical Notes (optional)", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pluginManager.connectApp(
                            context = context,
                            packageName = app.packageName,
                            teachingRole = role,
                            targetSubject = subject,
                            capabilities = app.suggestedCapabilities,
                            notes = notes,
                        )
                        selectedApp = null
                        onAppConnected()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PocketGreen),
                ) {
                    Text("Connect Plugin")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedApp = null }) { Text("Back") }
            },
        )
    }
}

@Composable
private fun SendPayloadDialog(
    plugin: AppPluginDefinition,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var payload by remember {
        mutableStateOf(
            when {
                plugin.capabilities.contains(AppPluginCapability.EXECUTE_CODE) ->
                    "print('Hello from Mobile Harness!')\n# Practice exercise\nx = [i**2 for i in range(10)]\nprint('Computed:', x)"
                plugin.capabilities.contains(AppPluginCapability.RENDER_GRAPH) ->
                    "f(x) = sin(x) * x^2"
                plugin.capabilities.contains(AppPluginCapability.SPACED_REPETITION) ->
                    "Concept: Derivatives\nRule: Power rule d/dx [x^n] = n * x^(n-1)"
                else ->
                    "Practice learning prompt for ${plugin.displayName}"
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Practice to ${plugin.displayName}", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This dispatches the practice payload or exercise directly to ${plugin.displayName} on your phone.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = { Text("Payload / Exercise Content") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(payload) },
                colors = ButtonDefaults.buttonColors(containerColor = PocketOrange),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Send & Launch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PocketOrange)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConceptRow(concept: Concept) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(concept.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (concept.isMastered) {
                    Text("Mastered ✓", color = PocketGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("Subject: ${concept.subject} · Attempts: ${concept.attempts}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { concept.mastery },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (concept.isMastered) PocketGreen else PocketOrange,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun ScratchpadTabContent(
    entries: List<ScratchpadEntry>,
    onAddEntry: (ScratchpadEntry) -> Unit,
    onDeleteEntry: (String) -> Unit,
    onClear: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var mathExpression by remember { mutableStateOf("") }
    var mathResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Deterministic Math Calculator", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = mathExpression,
                        onValueChange = { mathExpression = it },
                        placeholder = { Text("e.g. sqrt(144) + 2^4 * sin(pi/2)", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            mathResult = runCatching {
                                val res = DeterministicMathTool.evaluate(mathExpression)
                                "$res"
                            }.getOrElse { "Error: ${it.message}" }
                        },
                    ) {
                        Text("Calculate", fontSize = 12.sp)
                    }
                }
                if (mathResult != null) {
                    Text(
                        "Result: $mathResult",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = PocketGreen,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Visible Task Scratchpad", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row {
                TextButton(onClick = onClear) { Text("Clear All", fontSize = 12.sp) }
                Button(onClick = { showAddDialog = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("+ Add Note", fontSize = 12.sp)
                }
            }
        }

        if (entries.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Scratchpad is empty. Use it to store equations, checklists, or key derivations.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "[${entry.type.name}] ${entry.title}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { onDeleteEntry(entry.id) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                }
                            }
                            Text(entry.content, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        val selectedType by remember { mutableStateOf(ScratchpadType.NOTE) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Scratchpad Entry") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Content / Formula") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank()) {
                        onAddEntry(ScratchpadEntry(title = title, content = content, type = selectedType))
                        showAddDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SkillsTabContent(
    skills: List<SkillDefinition>,
    onAddSkill: (SkillDefinition, String?, String?) -> Unit,
    onToggleSkill: (String, Boolean) -> Unit,
    onDeleteSkill: (String) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Registered Skills & MCP Tools", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Button(onClick = { showCreateDialog = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                Text("+ New Skill", fontSize = 12.sp)
            }
        }

        if (skills.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No skills installed yet. Skills follow the standard SKILL.md format and can be created or imported.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(skills, key = { it.id }) { skill ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${skill.name} v${skill.version}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("By: ${skill.author} · ${if (skill.isAutoGenerated) "Self-Evolved" else "Standard"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = skill.isEnabled,
                                    onCheckedChange = { onToggleSkill(skill.id, it) },
                                )
                            }
                            Text(skill.description, fontSize = 12.sp)
                            if (skill.allowedPermissions.isNotEmpty()) {
                                Text(
                                    "Permissions: ${skill.allowedPermissions.joinToString(", ")}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = PocketOrange,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var id by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var instructions by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Skill") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("Skill Identifier (slug)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = instructions, onValueChange = { instructions = it }, label = { Text("Instructions / Playbook") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (id.isNotBlank() && name.isNotBlank()) {
                        val newSkill = SkillDefinition(
                            id = id.trim(),
                            name = name.trim(),
                            description = description.trim(),
                            instructions = instructions.trim(),
                        )
                        onAddSkill(newSkill, null, null)
                        showCreateDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AutonomyTabContent(
    activeMode: AutonomyMode,
    shizukuStatus: ShizukuStatus,
    onModeChanged: (AutonomyMode) -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Autonomy & Permission Gatekeeper", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Select how much execution authority the agent has when solving problems.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        AutonomyMode.entries.forEach { mode ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModeChanged(mode) },
                colors = CardDefaults.cardColors(
                    containerColor = if (activeMode == mode) PocketOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (activeMode == mode) PocketOrange else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                ),
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = activeMode == mode,
                        onClick = { onModeChanged(mode) },
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(mode.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(mode.description, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Text("Shizuku Elevated Android Integration", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (shizukuStatus == ShizukuStatus.AUTHORIZED_AND_READY) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (shizukuStatus == ShizukuStatus.AUTHORIZED_AND_READY) PocketGreen else PocketOrange,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Shizuku Status: ${shizukuStatus.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                ShizukuPrivilege.entries.forEach { priv ->
                    Text("• ${priv.title}: ${priv.benefitDescription}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        Text("Selectable Linux PRoot Environments", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        LinuxEnvironmentManager.AVAILABLE_DISTRIBUTIONS.forEach { distro ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(distro.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                        Text("Pkg: ${distro.packageManager}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Text(distro.description, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Download: ~${distro.downloadSizeMb} MB · Installed: ~${distro.installedSizeMb} MB", fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = PocketOrange)
                }
            }
        }
    }
}
