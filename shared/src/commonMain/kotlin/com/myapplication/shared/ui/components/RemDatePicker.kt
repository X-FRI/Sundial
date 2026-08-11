package com.myapplication.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myapplication.shared.ui.theme.LocalRemColors
import com.myapplication.shared.ui.theme.RemRadii
import com.myapplication.shared.ui.theme.RemType
import com.myapplication.shared.util.todayDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minusMonth
import kotlinx.datetime.number
import kotlinx.datetime.onDay
import kotlinx.datetime.plusMonth

@Composable
fun RemDatePicker(
    initialDate: LocalDate?,
    initialTime: LocalTime? = null,
    onPick: (LocalDate) -> Unit,
    onPickTime: (Int, Int) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
) {
    val colors = LocalRemColors.current
    val today = todayDate()
    var month by remember {
        mutableStateOf(
            initialDate?.let { YearMonth(it.year, it.month.number) }
                ?: YearMonth(today.year, today.month.number),
        )
    }
    RemDialog(
        title = "选择日期",
        onDismiss = onDismiss,
        confirmText = "确定",
        onConfirm = onDismiss,
        showButtons = false,
        content = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RemIconButton(IconName.ChevronBack, "上个月", onClick = { month = month.minusMonth() }, size = 14.dp)
                androidx.compose.foundation.text.BasicText(
                    "${month.year} 年 ${month.month.number} 月",
                    style = RemType.text14.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textHigh,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.weight(1f),
                )
                RemIconButton(IconName.ChevronRight, "下个月", onClick = { month = month.plusMonth() }, size = 14.dp)
            }
            Spacer(Modifier.height(12.dp))
            val offset = month.onDay(1).dayOfWeek.isoDayNumber - 1 // ISO 周一=1
            val daysInMonth = month.numberOfDays
            val weeks = (offset + daysInMonth + 6) / 7
            val weekHeaders = listOf("一", "二", "三", "四", "五", "六", "日")
            Row(Modifier.fillMaxWidth()) {
                weekHeaders.forEach {
                    androidx.compose.foundation.text.BasicText(
                        it,
                        style = RemType.label10.copy(color = colors.textLow, textAlign = TextAlign.Center),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            var day = 1
            for (w in 0 until weeks) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    for (c in 0 until 7) {
                        val idx = w * 7 + c
                        if (idx < offset || day > daysInMonth) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val d = day++
                            val date = LocalDate(month.year, month.month.number, d)
                            val isToday = date == today
                            val isSelected = date == initialDate
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(RemRadii.r2))
                                    .background(if (isSelected) colors.brand else Color.Transparent)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { onPick(date); onDismiss() },
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.foundation.text.BasicText(
                                    "$d",
                                    style = RemType.text14.copy(
                                        color = when {
                                            isSelected -> Color.White
                                            isToday -> colors.brand
                                            else -> colors.textHigh
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            TimePickerRow(initialTime = initialTime, onPickTime = onPickTime)
        })
}

@Composable
private fun TimePickerRow(initialTime: LocalTime?, onPickTime: (Int, Int) -> Unit) {
    val colors = LocalRemColors.current
    var hour by remember { mutableStateOf(initialTime?.hour ?: 9) }
    var minute by remember { mutableStateOf(initialTime?.minute ?: 0) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.text.BasicText("时间", style = RemType.text14.copy(color = colors.textNormal))
        Spacer(Modifier.weight(1f))
        RemIconButton(IconName.ChevronBack, "减一小时", onClick = {
            hour = (hour + 23) % 24
            onPickTime(hour, minute)
        }, size = 14.dp)
        androidx.compose.foundation.text.BasicText(
            "${hour.toString().padStart(2, '0')} : ${minute.toString().padStart(2, '0')}",
            style = RemType.text16.copy(color = colors.textHigh),
        )
        RemIconButton(IconName.ChevronRight, "加一小时", onClick = {
            hour = (hour + 1) % 24
            onPickTime(hour, minute)
        }, size = 14.dp)
        Spacer(Modifier.width(4.dp))
        RemIconButton(IconName.ChevronBack, "减五分钟", onClick = {
            minute = (minute + 55) % 60
            onPickTime(hour, minute)
        }, size = 14.dp)
        androidx.compose.foundation.text.BasicText("分", style = RemType.text12.copy(color = colors.textLow))
        RemIconButton(IconName.ChevronRight, "加五分钟", onClick = {
            minute = (minute + 5) % 60
            onPickTime(hour, minute)
        }, size = 14.dp)
        Spacer(Modifier.width(8.dp))
        RemButton("清除时间", onClick = { onPickTime(-1, -1) })
    }
}
