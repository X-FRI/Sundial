package com.myapplication.widget

import android.content.Context
import android.content.Intent
import arrow.core.getOrElse
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.myapplication.MainActivity
import com.myapplication.shared.data.setAndroidAppContext
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.di.createAppGraph
import com.myapplication.shared.domain.widget.TodayWidgetSnapshot
import com.myapplication.shared.domain.widget.WidgetSnapshotSize
import com.myapplication.shared.domain.widget.WidgetTask
import com.myapplication.shared.domain.widget.buildTodayWidgetSnapshot
import com.myapplication.shared.domain.widget.isCurrentFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

class TodayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(120.dp, 80.dp),
            DpSize(160.dp, 96.dp),
            DpSize(260.dp, 140.dp),
            DpSize(320.dp, 220.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = withContext(Dispatchers.IO) { loadSnapshot(context) }
        provideContent {
            val size = LocalSize.current
            val widgetSize = when {
                size.width < 220.dp || size.height < 120.dp -> WidgetSnapshotSize.Small
                size.height < 190.dp -> WidgetSnapshotSize.Medium
                else -> WidgetSnapshotSize.Large
            }
            TodayWidgetContent(context = context, snapshot = snapshot, size = widgetSize)
        }
    }

    private suspend fun loadSnapshot(context: Context): TodayWidgetSnapshot {
        val cache = WidgetSnapshotCache(context)
        setAndroidAppContext(context)
        val graph = try {
            createAppGraph()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return fallbackSnapshot(cache, Clock.System.now(), TimeZone.currentSystemDefault())
        }

        return try {
            val fresh = loadFreshSnapshot(graph)
            try {
                cache.write(fresh)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A fresh snapshot is still better than stale cache when persisting fails.
            }
            fresh
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            fallbackSnapshot(cache, graph.clock.now(), graph.timeZone)
        }
    }

    private suspend fun loadFreshSnapshot(graph: AppGraph): TodayWidgetSnapshot {
        val ensuredInboxId = graph.repository.ensureInbox().getOrElse { error ->
            throw IllegalStateException("Unable to ensure widget inbox: $error")
        }
        val lists = graph.repository.observeLists().first()
        val inboxListId = lists.firstOrNull { it.id == ensuredInboxId }?.id
        val todos = graph.repository.observeAllActive().first()
        return buildTodayWidgetSnapshot(
            todos = todos,
            now = graph.clock.now(),
            timeZone = graph.timeZone,
            inboxListId = inboxListId,
            maxTasks = WidgetSnapshotSize.Large.maxTodayTasks,
        )
    }

    private fun fallbackSnapshot(
        cache: WidgetSnapshotCache,
        now: kotlin.time.Instant,
        timeZone: TimeZone,
    ): TodayWidgetSnapshot =
        cache.read()
            ?.takeIf { snapshot -> snapshot.isCurrentFor(now, timeZone) }
            ?: TodayWidgetSnapshot.empty(now)
}

@Composable
private fun TodayWidgetContent(context: Context, snapshot: TodayWidgetSnapshot, size: WidgetSnapshotSize) {
    val todayIntent = launchIntent(context, MainActivity.TARGET_TODAY)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.Background)
            .clickable(actionStartActivity(launchIntent(context, MainActivity.TARGET_WORKBENCH)))
            .padding(
                when (size) {
                    WidgetSnapshotSize.Small -> 7.dp
                    WidgetSnapshotSize.Medium -> 12.dp
                    WidgetSnapshotSize.Large -> 12.dp
                },
            ),
    ) {
        when (size) {
            WidgetSnapshotSize.Small -> SmallTodayWidget(snapshot)
            WidgetSnapshotSize.Medium -> MediumTodayWidget(snapshot, todayIntent)
            WidgetSnapshotSize.Large -> LargeTodayWidget(snapshot, todayIntent)
        }
    }
}

@Composable
private fun SmallTodayWidget(snapshot: TodayWidgetSnapshot) {
    WidgetTitleRow(snapshot = snapshot, titleFontSize = 14, countFontSize = 11)
    Spacer(GlanceModifier.height(3.dp))
    Text(
        text = snapshot.nextTaskTitle?.let { title ->
            listOfNotNull(snapshot.nextTaskDueLabel, title).joinToString(" ")
        } ?: compactCounts(snapshot),
        style = TextStyle(color = WidgetColors.Body, fontSize = 11.sp, fontWeight = FontWeight.Medium),
        maxLines = 1,
    )
}

@Composable
private fun MediumTodayWidget(snapshot: TodayWidgetSnapshot, todayIntent: Intent) {
    WidgetTitleRow(snapshot = snapshot, titleFontSize = 16, countFontSize = 12)
    Spacer(GlanceModifier.height(5.dp))
    NextTaskText(snapshot = snapshot, fontSize = 12)
    Spacer(GlanceModifier.height(5.dp))
    snapshot.topTodayTasks.take(visibleTodayTasks(WidgetSnapshotSize.Medium)).forEach { task ->
        TaskLine(task = task, fontSize = 11, intent = todayIntent)
        Spacer(GlanceModifier.height(2.dp))
    }
    Spacer(GlanceModifier.height(2.dp))
    Text(
        text = compactCounts(snapshot),
        style = TextStyle(color = WidgetColors.Muted, fontSize = 10.sp),
        maxLines = 1,
    )
}

@Composable
private fun LargeTodayWidget(snapshot: TodayWidgetSnapshot, todayIntent: Intent) {
    SummaryChips(snapshot)
    Spacer(GlanceModifier.height(7.dp))
    snapshot.topTodayTasks.take(visibleTodayTasks(WidgetSnapshotSize.Large)).forEach { task ->
        TaskLine(task = task, fontSize = 11, intent = todayIntent)
        Spacer(GlanceModifier.height(3.dp))
    }
    if (snapshot.topOverdueTasks.isNotEmpty()) {
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = "逾期预览",
            style = TextStyle(color = WidgetColors.Warning, fontSize = 10.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(3.dp))
        snapshot.topOverdueTasks.take(visibleOverdueTasks(WidgetSnapshotSize.Large)).forEach { task ->
            Text(
                text = "· ${task.title}",
                style = TextStyle(color = WidgetColors.Muted, fontSize = 10.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(2.dp))
        }
    }
    Spacer(GlanceModifier.height(4.dp))
    Text(
        text = "更新 ${formatUpdatedAt(snapshot.lastUpdatedAt, TimeZone.currentSystemDefault())}",
        style = TextStyle(color = WidgetColors.Subtle, fontSize = 10.sp),
        maxLines = 1,
    )
}

@Composable
private fun WidgetTitleRow(snapshot: TodayWidgetSnapshot, titleFontSize: Int, countFontSize: Int) {
    Row(GlanceModifier.fillMaxWidth()) {
        Text(
            text = "今天",
            style = TextStyle(
                color = WidgetColors.Primary,
                fontSize = titleFontSize.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = "${snapshot.pendingTodayCount} 项",
            style = TextStyle(color = WidgetColors.Accent, fontSize = countFontSize.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun NextTaskText(snapshot: TodayWidgetSnapshot, fontSize: Int) {
    Text(
        text = snapshot.nextTaskTitle?.let { title ->
            "下一件 ${snapshot.nextTaskDueLabel ?: ""} $title".trim()
        } ?: "今天没有定时待办",
        style = TextStyle(color = WidgetColors.Body, fontSize = fontSize.sp, fontWeight = FontWeight.Medium),
        maxLines = 1,
    )
}

@Composable
private fun TaskLine(task: WidgetTask, fontSize: Int, intent: Intent) {
    Text(
        text = listOfNotNull(task.dueLabel, task.title).joinToString("  "),
        modifier = GlanceModifier.clickable(actionStartActivity(intent)),
        style = TextStyle(color = WidgetColors.Muted, fontSize = fontSize.sp),
        maxLines = 1,
    )
}

private fun launchIntent(context: Context, target: String): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .putExtra(MainActivity.EXTRA_SUNDIAL_TARGET, target)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

@Composable
private fun SummaryChips(snapshot: TodayWidgetSnapshot) {
    Row(GlanceModifier.fillMaxWidth()) {
        SummaryChip(label = "今天", value = snapshot.pendingTodayCount, emphasized = true)
        Spacer(GlanceModifier.width(6.dp))
        SummaryChip(label = "逾期", value = snapshot.overdueCount)
        Spacer(GlanceModifier.width(6.dp))
        SummaryChip(label = "待整理", value = snapshot.inboxCount)
        Spacer(GlanceModifier.width(6.dp))
        SummaryChip(label = "完成", value = snapshot.completedTodayCount)
    }
}

@Composable
private fun SummaryChip(label: String, value: Int, emphasized: Boolean = false) {
    Text(
        text = "$label $value",
        modifier = GlanceModifier
            .background(if (emphasized) WidgetColors.AccentSurface else WidgetColors.ChipBackground)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        style = TextStyle(
            color = if (emphasized) WidgetColors.Accent else WidgetColors.Body,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        ),
        maxLines = 1,
    )
}

private fun visibleTodayTasks(size: WidgetSnapshotSize): Int =
    when (size) {
        WidgetSnapshotSize.Small -> 0
        WidgetSnapshotSize.Medium -> 2
        WidgetSnapshotSize.Large -> 4
    }

private fun visibleOverdueTasks(size: WidgetSnapshotSize): Int =
    when (size) {
        WidgetSnapshotSize.Small -> 0
        WidgetSnapshotSize.Medium -> 0
        WidgetSnapshotSize.Large -> 2
    }

private fun compactCounts(snapshot: TodayWidgetSnapshot): String =
    "逾期 ${snapshot.overdueCount} · 待整理 ${snapshot.inboxCount} · 完成 ${snapshot.completedTodayCount}"

private fun formatUpdatedAt(instant: Instant, timeZone: TimeZone): String {
    val time = instant.toLocalDateTime(timeZone).time
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}

private object WidgetColors {
    val Background = ColorProvider(Color(0xFFFFFFFF))
    val Primary = ColorProvider(Color(0xFF0D0D0D))
    val Body = ColorProvider(Color(0xFF333333))
    val Muted = ColorProvider(Color(0xFF636363))
    val Subtle = ColorProvider(Color(0xFF8C8C8C))
    val Accent = ColorProvider(Color(0xFFEA7A2A))
    val Warning = ColorProvider(Color(0xFFB45309))
    val AccentSurface = ColorProvider(Color(0xFFFFF1E6))
    val ChipBackground = ColorProvider(Color(0xFFF3F4F6))
}
