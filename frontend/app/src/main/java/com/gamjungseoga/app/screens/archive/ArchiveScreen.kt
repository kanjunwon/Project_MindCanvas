package com.gamjungseoga.app.screens.archive

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamjungseoga.app.components.WheelPicker
import com.gamjungseoga.app.emotion.drawableForEmotion
import com.gamjungseoga.app.network.DiaryEntry
import com.gamjungseoga.app.screens.diary.DiaryListState
import com.gamjungseoga.app.screens.diary.DiaryListViewModel
import com.gamjungseoga.app.ui.theme.BodyGray
import com.gamjungseoga.app.ui.theme.ButtonMint
import com.gamjungseoga.app.ui.theme.MonthLabelGray
import com.gamjungseoga.app.ui.theme.NavInactiveGray
import com.gamjungseoga.app.ui.theme.SurfaceColor
import com.gamjungseoga.app.ui.theme.TitleBrown
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val archiveHeaderDateFormatter = DateTimeFormatter.ofPattern("yyyy.MM")
private val archiveCardDateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

// created_at은 Supabase가 ISO 8601(timestamptz)로 내려줌. 파싱 실패하면 원본 앞 10자리로 대체.
private fun formatArchiveDate(createdAt: String?): String {
    if (createdAt == null) return ""
    return try {
        OffsetDateTime.parse(createdAt).format(archiveCardDateFormatter)
    } catch (_: Exception) {
        createdAt.take(10).replace("-", ".")
    }
}

private fun archiveTitle(entry: DiaryEntry): String =
    entry.topEmotion?.let { "${it} 날" } ?: "기록한 날"

// entry의 created_at을 파싱해서 선택한 연/월과 같은 달인지 확인 (파싱 실패한 항목은 어느 달에도 안 걸림)
private fun DiaryEntry.isInMonth(yearMonth: YearMonth): Boolean {
    val created = createdAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: return false
    return YearMonth.from(created) == yearMonth
}

@Composable
fun ArchiveScreen(diaryListViewModel: DiaryListViewModel = viewModel()) {
    var selectedYearMonth by remember { mutableStateOf(YearMonth.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val listState = diaryListViewModel.state
    val monthEntries = remember(listState, selectedYearMonth) {
        (listState as? DiaryListState.Loaded)?.diaries?.filter { it.isInMonth(selectedYearMonth) }.orEmpty()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 49.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(25.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ArchiveHeader(
                yearMonth = selectedYearMonth,
                onDateClick = { showDatePicker = true }
            )
        }
        when (listState) {
            is DiaryListState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                Text("불러오는 중...", style = MaterialTheme.typography.bodyMedium, color = MonthLabelGray)
            }
            is DiaryListState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "일기 목록을 불러오지 못했어요. (${listState.message})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BodyGray
                )
            }
            is DiaryListState.Loaded -> {
                if (monthEntries.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("이 달에 기록한 일기가 없어요.", style = MaterialTheme.typography.bodyMedium, color = MonthLabelGray)
                    }
                } else {
                    items(monthEntries) { entry ->
                        ArchiveCard(entry)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        ArchiveDatePickerDialog(
            initialYearMonth = selectedYearMonth,
            onConfirm = {
                selectedYearMonth = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
private fun ArchiveHeader(yearMonth: YearMonth, onDateClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("나의 감정 서재", style = MaterialTheme.typography.headlineSmall, color = TitleBrown)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onDateClick)
        ) {
            Text(yearMonth.format(archiveHeaderDateFormatter), style = MaterialTheme.typography.bodyMedium, color = TitleBrown)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "날짜 선택", tint = TitleBrown)
        }
    }
}

@Composable
private fun ArchiveCard(entry: DiaryEntry) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            // SD3 생성 이미지가 붙기 전까지는 top_emotion 기준으로 가장 가까운 기존 일러스트로 대체
            painter = painterResource(drawableForEmotion(entry.topEmotion)),
            contentDescription = archiveTitle(entry),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(314f / 429f)
                .clip(RoundedCornerShape(9.dp))
        )
        Spacer(Modifier.height(10.dp))
        Text(archiveTitle(entry), style = MaterialTheme.typography.titleSmall, color = TitleBrown)
        Text(formatArchiveDate(entry.createdAt), style = MaterialTheme.typography.labelSmall, color = MonthLabelGray)
    }
}

private val datePickerItemHeight = 56.dp

@Composable
private fun ArchiveDatePickerDialog(
    initialYearMonth: YearMonth,
    onConfirm: (YearMonth) -> Unit,
    onDismiss: () -> Unit
) {
    var year by remember { mutableStateOf(initialYearMonth.year) }
    var month by remember { mutableStateOf(initialYearMonth.monthValue) }
    val years = remember { ((initialYearMonth.year - 10)..(initialYearMonth.year + 10)).toList() }
    val months = remember { (1..12).toList() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = SurfaceColor,
            shape = RoundedCornerShape(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("날짜 선택", style = MaterialTheme.typography.titleMedium, color = TitleBrown)
                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                    ) {
                        HorizontalDivider(color = NavInactiveGray)
                        Spacer(Modifier.height(datePickerItemHeight))
                        HorizontalDivider(color = NavInactiveGray)
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WheelPicker(
                            items = years,
                            selectedIndex = years.indexOf(year).coerceAtLeast(0),
                            onSelectedIndexChange = { year = years[it] },
                            itemHeight = datePickerItemHeight,
                            infinite = false,
                            modifier = Modifier.width(140.dp)
                        ) { item, selected ->
                            DatePickerWheelLabel("${item}년", selected)
                        }
                        WheelPicker(
                            items = months,
                            selectedIndex = month - 1,
                            onSelectedIndexChange = { month = months[it] },
                            itemHeight = datePickerItemHeight,
                            infinite = true,
                            modifier = Modifier.width(100.dp)
                        ) { item, selected ->
                            DatePickerWheelLabel("${item}월", selected)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = onDismiss,
                        color = MonthLabelGray.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                            Text("취소", style = MaterialTheme.typography.bodyMedium, color = TitleBrown)
                        }
                    }
                    Surface(
                        onClick = { onConfirm(YearMonth.of(year, month)) },
                        color = ButtonMint,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                            Text("확인", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DatePickerWheelLabel(text: String, selected: Boolean) {
    Text(
        text,
        style = if (selected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        color = if (selected) TitleBrown else MonthLabelGray
    )
}
