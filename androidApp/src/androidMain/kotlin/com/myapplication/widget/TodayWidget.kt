package com.myapplication.widget

import android.content.Context
import arrow.core.getOrElse
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.myapplication.MainActivity
import com.myapplication.shared.data.setAndroidAppContext
import com.myapplication.shared.di.AppGraph
import com.myapplication.shared.di.createAppGraph
import com.myapplication.shared.domain.widget.TodayWidgetSnapshot
import com.myapplication.shared.domain.widget.buildTodayWidgetSnapshot
import com.myapplication.shared.domain.widget.isCurrentFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = withContext(Dispatchers.IO) { loadSnapshot(context) }
        provideContent {
            TodayWidgetContent(snapshot)
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
            maxTasks = 3,
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
private fun TodayWidgetContent(snapshot: TodayWidgetSnapshot) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFFFFFFFF)))
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth()) {
            Text(
                text = "今天",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF0D0D0D)),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = "${snapshot.pendingTodayCount} 项",
                style = TextStyle(color = ColorProvider(Color(0xFFEA7A2A)), fontSize = 14.sp, fontWeight = FontWeight.Medium),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = snapshot.nextTaskTitle?.let { title ->
                "下一件 ${snapshot.nextTaskDueLabel ?: ""} $title".trim()
            } ?: "今天没有定时待办",
            style = TextStyle(color = ColorProvider(Color(0xFF333333)), fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(8.dp))
        snapshot.topTodayTasks.take(3).forEach { task ->
            Text(
                text = listOfNotNull(task.dueLabel, task.title).joinToString("  "),
                style = TextStyle(color = ColorProvider(Color(0xFF636363)), fontSize = 12.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(4.dp))
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = "逾期 ${snapshot.overdueCount} · 待整理 ${snapshot.inboxCount} · 完成 ${snapshot.completedTodayCount}",
            style = TextStyle(color = ColorProvider(Color(0xFF636363)), fontSize = 11.sp),
            maxLines = 1,
        )
    }
}
