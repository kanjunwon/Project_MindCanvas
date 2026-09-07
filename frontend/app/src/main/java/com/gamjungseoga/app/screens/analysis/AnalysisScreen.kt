package com.gamjungseoga.app.screens.analysis

import com.gamjungseoga.app.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.gamjungseoga.app.network.ApiClient
import com.gamjungseoga.app.network.DailyStatsResponse
import com.gamjungseoga.app.network.EmotionDistributionItem
import com.gamjungseoga.app.network.EmotionFlowPoint
import com.gamjungseoga.app.network.EmotionScore
import com.gamjungseoga.app.network.MonthlyStatsResponse
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.gamjungseoga.app.components.WheelPicker
import com.gamjungseoga.app.ui.theme.AccentBlue
import com.gamjungseoga.app.ui.theme.AccentGreen
import com.gamjungseoga.app.ui.theme.AccentNavy
import com.gamjungseoga.app.ui.theme.AccentOrange
import com.gamjungseoga.app.ui.theme.AccentPurple
import com.gamjungseoga.app.ui.theme.AccentTerracotta
import com.gamjungseoga.app.ui.theme.BodyGray
import com.gamjungseoga.app.ui.theme.ButtonMint
import com.gamjungseoga.app.ui.theme.ChartMint
import com.gamjungseoga.app.ui.theme.EmptyGray
import com.gamjungseoga.app.ui.theme.HighlightBlue
import com.gamjungseoga.app.ui.theme.HighlightMint
import com.gamjungseoga.app.ui.theme.LoverPink
import com.gamjungseoga.app.ui.theme.MonthLabelGray
import com.gamjungseoga.app.ui.theme.NavInactiveGray
import com.gamjungseoga.app.ui.theme.NegativeDateNavy
import com.gamjungseoga.app.ui.theme.PillBlueBg
import com.gamjungseoga.app.ui.theme.PillGreenBg
import com.gamjungseoga.app.ui.theme.PillOrangeBg
import com.gamjungseoga.app.ui.theme.PillPinkBg
import com.gamjungseoga.app.ui.theme.PillPurpleBg
import com.gamjungseoga.app.ui.theme.PositiveDatePink
import com.gamjungseoga.app.ui.theme.RibbonPink
import com.gamjungseoga.app.ui.theme.SCoreDreamFontFamily
import com.gamjungseoga.app.ui.theme.SchoolBlue
import com.gamjungseoga.app.ui.theme.SolidGreen
import com.gamjungseoga.app.ui.theme.SurfaceColor
import com.gamjungseoga.app.ui.theme.TabInactiveGray
import com.gamjungseoga.app.ui.theme.TitleBrown
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private enum class ReportTab(val label: String) { DAILY("일간"), MONTHLY("월간") }

private val analysisDateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val analysisMonthFormatter = DateTimeFormatter.ofPattern("yyyy.MM")

data class TopEmotionBar(val label: String, val percent: Int, val barHeight: Dp, val color: Color)

private val dailyBarColors = listOf(ChartMint, AccentGreen, RibbonPink)
private val dailyBarMaxHeight = 138.dp

private val monthlyBarColors = listOf(AccentBlue, AccentPurple, AccentNavy)

// GET /stats/daily, /stats/monthly의 top3_emotions는 여러 일기의 점수를 그냥 합산한 값이라(1.0 안
// 넘게 정규화돼있지 않음) 3개 막대끼리 상대 비중으로 다시 나눠서 60/30/10 같은 퍼센트를 만듦
private fun toTopEmotionBars(scores: List<EmotionScore>, palette: List<Color> = dailyBarColors): List<TopEmotionBar> {
    val total = scores.sumOf { it.score }
    if (scores.isEmpty() || total <= 0) return emptyList()
    return scores.mapIndexed { index, item ->
        val percent = (item.score / total * 100).toInt().coerceIn(0, 100)
        TopEmotionBar(
            label = item.emotion,
            percent = percent,
            barHeight = dailyBarMaxHeight * (percent / 100f).coerceAtLeast(0.15f),
            color = palette[index % palette.size]
        )
    }
}

data class WeekPoint(val label: String, val value: Float)

// emotion_flow는 하루 단위 sentiment_score만 내려주므로, 그 달 안에서 일(day) 순서대로 7일씩
// 묶어 주차 평균을 내고, 차트가 쓰기 좋게 그 달 안에서의 최소/최대로 다시 0~1로 정규화함
// (sentiment_score의 실제 범위를 백엔드 코드를 보지 않고는 알 수 없어서, 상대적인 높낮이만 표현)
private fun toWeeklyFlow(emotionFlow: List<EmotionFlowPoint>): List<WeekPoint> {
    val parsed = emotionFlow.mapNotNull { point ->
        runCatching { LocalDate.parse(point.date) }.getOrNull()?.let { it to point.sentimentScore }
    }
    if (parsed.isEmpty()) return emptyList()

    val weeklyAverages = parsed
        .groupBy { (date, _) -> (date.dayOfMonth - 1) / 7 }
        .toSortedMap()
        .map { (weekIndex, entries) -> weekIndex to entries.map { it.second }.average() }

    val min = weeklyAverages.minOf { it.second }
    val max = weeklyAverages.maxOf { it.second }
    val range = (max - min).takeIf { it > 0.0 }

    return weeklyAverages.map { (weekIndex, avg) ->
        val normalized = range?.let { ((avg - min) / it).toFloat() } ?: 0.5f
        WeekPoint(label = "${weekIndex + 1}주차", value = normalized.coerceIn(0.05f, 1f))
    }
}

// GET /stats/monthly의 emotion_distribution이 감정별 색상(hex)을 같이 내려주므로, 그 색을
// 파싱해서 캘린더 셀/대표 감정 강조색으로 그대로 재사용 (프론트에서 감정-색 매핑을 새로 만들지 않음)
private fun parseHexColor(hex: String): Color? = runCatching {
    val cleaned = hex.removePrefix("#")
    val argb = when (cleaned.length) {
        6 -> 0xFF000000L or cleaned.toLong(16)
        8 -> cleaned.toLong(16)
        else -> return null
    }
    Color(argb.toInt())
}.getOrNull()

private fun colorForEmotion(emotion: String?, distribution: List<EmotionDistributionItem>): Color? {
    if (emotion == null) return null
    val hex = distribution.firstOrNull { it.emotion == emotion }?.color ?: return null
    return parseHexColor(hex)
}

// 이번 달 캘린더를 월요일 시작 7열 그리드로 채움. 일기가 없는 날은 null(빈 칸),
// 일기는 있는데 색을 못 찾은 날은 EmptyGray로 표시.
private fun toDayColors(yearMonth: YearMonth, stats: MonthlyStatsResponse): List<Color?> {
    val flowByDate = stats.emotionFlow.associateBy { it.date }
    val leadingBlanks = yearMonth.atDay(1).dayOfWeek.value - 1
    val cells = mutableListOf<Color?>()
    repeat(leadingBlanks) { cells += null }
    for (day in 1..yearMonth.lengthOfMonth()) {
        val dateStr = yearMonth.atDay(day).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val flowPoint = flowByDate[dateStr]
        cells += when {
            flowPoint == null -> null
            else -> colorForEmotion(flowPoint.topEmotion, stats.emotionDistribution) ?: EmptyGray
        }
    }
    while (cells.size % 7 != 0) cells += null
    return cells
}

private val feltDayFormatter = DateTimeFormatter.ofPattern("M월 d일")
private fun formatFeltDate(dateStr: String?): String =
    dateStr?.let { runCatching { LocalDate.parse(it).format(feltDayFormatter) }.getOrNull() } ?: "-"

// 특정 날짜의 top3_emotions(daily 통계)를 긍정/부정 카드용 막대로 변환
private fun toFeltEmotionBars(scores: List<EmotionScore>, color: Color): List<FeltEmotionBar> {
    val total = scores.sumOf { it.score }
    if (scores.isEmpty() || total <= 0) return emptyList()
    return scores.map { item ->
        FeltEmotionBar(label = item.emotion, percent = (item.score / total * 100).toInt().coerceIn(0, 100), color = color)
    }
}

private val personPillPalette = listOf(RibbonPink to PillPinkBg, AccentGreen to PillGreenBg, AccentOrange to PillOrangeBg)
private val placePillPalette = listOf(AccentNavy to PillBlueBg, AccentPurple to PillPurpleBg, AccentBlue to PillBlueBg)

// top_companion/top_place의 top3_emotions는 점수 합산값이라 진짜 "횟수"는 아니지만, 배지에 쓸
// 정수가 필요해서 반올림해서 씀 (정확한 횟수가 필요하면 백엔드가 별도 필드로 내려줘야 함)
private fun toPills(scores: List<EmotionScore>, palette: List<Pair<Color, Color>>): List<PillStat> =
    scores.mapIndexed { index, item ->
        val (dot, bg) = palette[index % palette.size]
        PillStat(label = item.emotion, count = item.score.roundToInt().coerceAtLeast(0), dotColor = dot, bgColor = bg)
    }

private data class EmotionCategory(val label: String, val color: Color)

private val monthlyEmotionCategories = listOf(
    EmotionCategory("긍정", RibbonPink),
    EmotionCategory("활력", AccentOrange),
    EmotionCategory("성취", AccentGreen),
    EmotionCategory("안정", AccentBlue),
    EmotionCategory("침체", AccentNavy),
    EmotionCategory("긴장", AccentPurple),
    EmotionCategory("고갈", AccentTerracotta),
    EmotionCategory("분노", ChartMint)
)

data class FeltEmotionBar(val label: String, val percent: Int, val color: Color)

data class PillStat(val label: String, val count: Int, val dotColor: Color, val bgColor: Color)

// 화면에 필요한 월간 리포트 값들을 한데 묶는 그릇. GET /stats/monthly 응답 + 가장 긍정/부정적인
// 날의 daily 통계(top3_emotions)를 조합해서 만듦 (toMonthlyReportData 참고)
data class MonthlyReportData(
    val topEmotion: String,
    val topEmotionPercent: Int,
    val topEmotionColor: Color,
    val topEmotions: List<TopEmotionBar>,
    val weeklyFlow: List<WeekPoint>,
    val dayColors: List<Color?>,
    val positiveFeltDate: String,
    val positiveFeltBars: List<FeltEmotionBar>,
    val negativeFeltDate: String,
    val negativeFeltBars: List<FeltEmotionBar>,
    val personLabel: String,
    val personFeelingHighlight: String,
    val personPills: List<PillStat>,
    val placeLabel: String,
    val placeFeelingHighlight: String,
    val placePills: List<PillStat>
)

// GET /stats/monthly 응답과, 가장 긍정/부정적인 날 각각의 daily 통계(top3_emotions)를 합쳐서
// 화면이 쓰는 형태로 변환. topCompanion/topPlace가 없으면(그 달에 who/where 기록이 없으면)
// personLabel/placeLabel은 "기록 없음", 관련 pills는 빈 목록으로 내려감
private fun toMonthlyReportData(
    yearMonth: YearMonth,
    stats: MonthlyStatsResponse,
    positiveDayStats: DailyStatsResponse?,
    negativeDayStats: DailyStatsResponse?
): MonthlyReportData {
    val topBars = toTopEmotionBars(stats.top3Emotions, monthlyBarColors)
    return MonthlyReportData(
        topEmotion = stats.topEmotion ?: "-",
        topEmotionPercent = topBars.firstOrNull()?.percent ?: 0,
        topEmotionColor = colorForEmotion(stats.topEmotion, stats.emotionDistribution) ?: HighlightBlue,
        topEmotions = topBars,
        weeklyFlow = toWeeklyFlow(stats.emotionFlow),
        dayColors = toDayColors(yearMonth, stats),
        positiveFeltDate = formatFeltDate(stats.mostPositiveDay),
        positiveFeltBars = toFeltEmotionBars(positiveDayStats?.top3Emotions.orEmpty(), RibbonPink),
        negativeFeltDate = formatFeltDate(stats.mostNegativeDay),
        negativeFeltBars = toFeltEmotionBars(negativeDayStats?.top3Emotions.orEmpty(), AccentNavy),
        personLabel = stats.topCompanion?.name ?: "기록 없음",
        personFeelingHighlight = stats.topCompanion?.top3Emotions?.firstOrNull()?.emotion ?: "-",
        personPills = toPills(stats.topCompanion?.top3Emotions.orEmpty(), personPillPalette),
        placeLabel = stats.topPlace?.name ?: "기록 없음",
        placeFeelingHighlight = stats.topPlace?.top3Emotions?.firstOrNull()?.emotion ?: "-",
        placePills = toPills(stats.topPlace?.top3Emotions.orEmpty(), placePillPalette)
    )
}

@Composable
fun AnalysisScreen() {
    var selectedTab by remember { mutableStateOf(ReportTab.DAILY) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    var dailyStats by remember { mutableStateOf<DailyStatsResponse?>(null) }
    var dailyLoading by remember { mutableStateOf(true) }
    var dailyError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedDate) {
        dailyLoading = true
        dailyError = null
        try {
            dailyStats = ApiClient.statsApi.getDailyStats(
                userId = ApiClient.TEST_USER_ID,
                date = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            )
        } catch (e: Exception) {
            dailyError = e.message ?: "통계를 불러오지 못했어요."
        } finally {
            dailyLoading = false
        }
    }

    var monthlyStats by remember { mutableStateOf<MonthlyStatsResponse?>(null) }
    var monthlyLoading by remember { mutableStateOf(true) }
    var monthlyError by remember { mutableStateOf<String?>(null) }
    var positiveDayStats by remember { mutableStateOf<DailyStatsResponse?>(null) }
    var negativeDayStats by remember { mutableStateOf<DailyStatsResponse?>(null) }

    LaunchedEffect(selectedMonth) {
        monthlyLoading = true
        monthlyError = null
        positiveDayStats = null
        negativeDayStats = null
        try {
            val stats = ApiClient.statsApi.getMonthlyStats(
                userId = ApiClient.TEST_USER_ID,
                year = selectedMonth.year,
                month = selectedMonth.monthValue
            )
            monthlyStats = stats
            // 가장 긍정/부정적인 날의 감정 breakdown은 월간 API에 없어서, 그 날짜로 daily API를
            // 한 번씩 더 불러옴. 실패해도(예: 그 사이 서버가 끊김) 월간 리포트 전체를 막지는 않음
            stats.mostPositiveDay?.let { date ->
                positiveDayStats = runCatching {
                    ApiClient.statsApi.getDailyStats(ApiClient.TEST_USER_ID, date)
                }.getOrNull()
            }
            stats.mostNegativeDay?.let { date ->
                negativeDayStats = runCatching {
                    ApiClient.statsApi.getDailyStats(ApiClient.TEST_USER_ID, date)
                }.getOrNull()
            }
        } catch (e: Exception) {
            monthlyError = e.message ?: "월간 통계를 불러오지 못했어요."
        } finally {
            monthlyLoading = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                text = "나의 감정 리포트",
                style = MaterialTheme.typography.headlineSmall,
                color = TitleBrown,
                modifier = Modifier.fillMaxWidth().padding(top = 49.dp),
                textAlign = TextAlign.Center
            )
        }
        item {
            Spacer(Modifier.height(20.dp))
            ReportTabRow(selectedTab) { selectedTab = it }
        }
        item {
            Spacer(Modifier.height(24.dp))
            if (selectedTab == ReportTab.DAILY) {
                DateNav(
                    dateText = selectedDate.format(analysisDateFormatter),
                    onPrev = { selectedDate = selectedDate.minusDays(1) },
                    onNext = { selectedDate = selectedDate.plusDays(1) },
                    onDateClick = { showDatePicker = true }
                )
            } else {
                DateNav(
                    dateText = selectedMonth.format(analysisMonthFormatter),
                    onPrev = { selectedMonth = selectedMonth.minusMonths(1) },
                    onNext = { selectedMonth = selectedMonth.plusMonths(1) },
                    onDateClick = { showDatePicker = true }
                )
            }
        }

        if (selectedTab == ReportTab.DAILY) {
            when {
                dailyLoading -> item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "불러오는 중...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BodyGray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                dailyError != null -> item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "통계를 불러오지 못했어요. (${dailyError})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BodyGray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                dailyStats?.topEmotion == null -> item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "이 날 기록된 일기가 없어요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BodyGray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                else -> {
                    val stats = dailyStats!!
                    val bars = toTopEmotionBars(stats.top3Emotions)
                    item {
                        Spacer(Modifier.height(24.dp))
                        TodaySummaryCard(emotion = stats.topEmotion ?: "-", percent = bars.firstOrNull()?.percent ?: 0)
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                        TopEmotionsCard(bars)
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InfoCard(
                                modifier = Modifier.weight(1f),
                                iconRes = R.drawable.analysis_person_icon,
                                prefix = "오늘 함께한 사람은",
                                highlight = stats.companions.firstOrNull() ?: "기록 없음",
                                suffix = "에요"
                            )
                            InfoCard(
                                modifier = Modifier.weight(1f),
                                iconRes = R.drawable.analysis_location_icon,
                                prefix = "오늘 방문한 장소는",
                                highlight = stats.places.firstOrNull() ?: "기록 없음",
                                suffix = "에요"
                            )
                        }
                    }
                }
            }
        } else {
            when {
                monthlyLoading -> item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "불러오는 중...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BodyGray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                monthlyError != null -> item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "월간 통계를 불러오지 못했어요. (${monthlyError})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BodyGray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                monthlyStats?.topEmotion == null -> item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "이 달에 기록된 일기가 없어요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BodyGray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                else -> {
                    val monthlyReport = toMonthlyReportData(selectedMonth, monthlyStats!!, positiveDayStats, negativeDayStats)
                    item {
                        Spacer(Modifier.height(24.dp))
                        MonthlySummaryCard(
                            emotion = monthlyReport.topEmotion,
                            percent = monthlyReport.topEmotionPercent,
                            emotionColor = monthlyReport.topEmotionColor
                        )
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                        TopEmotionsCard(monthlyReport.topEmotions)
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                        EmotionFlowCard(monthlyReport.weeklyFlow)
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                        EmotionDistributionCard(monthlyReport.dayColors)
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                        TopFeltDayCard(
                            subtitlePrefix = "가장 긍정적인 감정을 느낀 날은 ",
                            dateHighlight = monthlyReport.positiveFeltDate,
                            dateColor = PositiveDatePink,
                            description = monthlyReport.positiveFeltBars.firstOrNull()?.label?.let { "$it 감정이 가장 많았어요" }
                                ?: "기록된 감정이 없어요",
                            bars = monthlyReport.positiveFeltBars
                        )
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                        TopFeltDayCard(
                            subtitlePrefix = "가장 부정적인 감정을 느낀 날은 ",
                            dateHighlight = monthlyReport.negativeFeltDate,
                            dateColor = NegativeDateNavy,
                            description = monthlyReport.negativeFeltBars.firstOrNull()?.label?.let { "$it 감정이 가장 많았어요" }
                                ?: "기록된 감정이 없어요",
                            bars = monthlyReport.negativeFeltBars
                        )
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                        PersonPlaceCard(
                            iconRes = R.drawable.analysis_person_icon,
                            highlightPrefix = "가장 많이 함께한 사람은 ",
                            highlight = monthlyReport.personLabel,
                            highlightColor = LoverPink,
                            recordLine = "${monthlyReport.personLabel}과 함께했던 기록이에요",
                            feelingPrefix = "${monthlyReport.personLabel}은 나에게 ",
                            feelingHighlight = monthlyReport.personFeelingHighlight,
                            feelingColor = LoverPink,
                            feelingSuffix = " 감정을 느끼게 해요",
                            pills = monthlyReport.personPills
                        )
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                        PersonPlaceCard(
                            iconRes = R.drawable.analysis_location_icon,
                            highlightPrefix = "가장 많이 방문한 장소는 ",
                            highlight = monthlyReport.placeLabel,
                            highlightColor = SchoolBlue,
                            recordLine = "${monthlyReport.placeLabel}에서 느꼈던 기록이에요",
                            feelingPrefix = "${monthlyReport.placeLabel}는 나에게 ",
                            feelingHighlight = monthlyReport.placeFeelingHighlight,
                            feelingColor = SchoolBlue,
                            feelingSuffix = " 감정을 느끼게 해요",
                            pills = monthlyReport.placePills
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        AnalysisDatePickerDialog(
            initialDate = if (selectedTab == ReportTab.DAILY) selectedDate else selectedMonth.atDay(1),
            includeDay = selectedTab == ReportTab.DAILY,
            onConfirm = { picked ->
                if (selectedTab == ReportTab.DAILY) {
                    selectedDate = picked
                } else {
                    selectedMonth = YearMonth.from(picked)
                }
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
private fun ReportTabRow(selected: ReportTab, onSelect: (ReportTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        ReportTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(84.dp)
                    .padding(horizontal = 24.dp)
                    .clickable { onSelect(tab) }
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSelected) SolidGreen else TabInactiveGray
                )
                Spacer(Modifier.height(6.dp))
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(SolidGreen)
                    )
                }
            }
        }
    }
}

@Composable
private fun DateNav(dateText: String, onPrev: () -> Unit, onNext: () -> Unit, onDateClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "이전 날짜", tint = TitleBrown)
            }
            Text(dateText, style = MaterialTheme.typography.titleMedium, color = TitleBrown)
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "다음 날짜", tint = TitleBrown)
            }
        }
        IconButton(onClick = onDateClick, modifier = Modifier.align(Alignment.CenterEnd)) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = "날짜 선택", tint = TitleBrown)
        }
    }
}

@Composable
private fun TodaySummaryCard(emotion: String, percent: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = SurfaceColor,
        shape = RoundedCornerShape(23.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.analysis_top_emotion_circles),
                contentDescription = null,
                modifier = Modifier.size(width = 88.dp, height = 63.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = buildAnnotatedString {
                        append("오늘 가장 많이 느낀 감정은\n")
                        withStyle(SpanStyle(color = HighlightMint, fontWeight = FontWeight.Bold)) {
                            append(emotion)
                        }
                        append("이에요")
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SCoreDreamFontFamily),
                    color = Color.Black
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "전체 감정 중 ${percent}%를 차지했어요",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = SCoreDreamFontFamily),
                    color = BodyGray
                )
            }
        }
    }
}

@Composable
private fun TopEmotionsCard(emotions: List<TopEmotionBar>) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = SurfaceColor,
        shape = RoundedCornerShape(23.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "주요 감정",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SCoreDreamFontFamily, fontWeight = FontWeight.Bold),
                    color = Color.Black
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "일기에서 추출된 핵심 감정이에요",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = SCoreDreamFontFamily),
                    color = BodyGray
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                emotions.forEach { bar ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(88.dp)
                                .height(bar.barHeight)
                                .background(bar.color, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text(
                                "${bar.percent}%",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SCoreDreamFontFamily, fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            bar.label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SCoreDreamFontFamily),
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    modifier: Modifier = Modifier,
    iconRes: Int,
    prefix: String,
    highlight: String,
    suffix: String
) {
    Surface(
        modifier = modifier,
        color = SurfaceColor,
        shape = RoundedCornerShape(23.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                prefix,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = SCoreDreamFontFamily),
                color = Color.Black
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = HighlightMint, fontWeight = FontWeight.Bold)) {
                        append(highlight)
                    }
                    append(suffix)
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SCoreDreamFontFamily),
                color = Color.Black
            )
        }
    }
}

@Composable
private fun MonthlySummaryCard(emotion: String, percent: Int, emotionColor: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = SurfaceColor,
        shape = RoundedCornerShape(23.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.analysis_top_emotion_circles),
                contentDescription = null,
                modifier = Modifier.size(width = 88.dp, height = 63.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = buildAnnotatedString {
                        append("이번 달 가장 많이 느낀 감정은\n")
                        withStyle(SpanStyle(color = emotionColor, fontWeight = FontWeight.Bold)) {
                            append(emotion)
                        }
                        append("이에요")
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SCoreDreamFontFamily),
                    color = Color.Black
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "전체 감정 중 ${percent}%를 차지했어요",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = SCoreDreamFontFamily),
                    color = BodyGray
                )
            }
        }
    }
}

@Composable
private fun EmotionFlowCard(weeklyFlow: List<WeekPoint>) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = SurfaceColor,
        shape = RoundedCornerShape(23.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "감정 흐름",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SCoreDreamFontFamily, fontWeight = FontWeight.Bold),
                color = Color.Black
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "이번 달의 감정의 흐름을 살펴보세요",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = SCoreDreamFontFamily),
                color = BodyGray
            )
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .height(160.dp)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("좋은 날", style = MaterialTheme.typography.labelSmall.copy(fontFamily = SCoreDreamFontFamily), color = BodyGray)
                    Text("평범한 날", style = MaterialTheme.typography.labelSmall.copy(fontFamily = SCoreDreamFontFamily), color = BodyGray)
                    Text("안좋은 날", style = MaterialTheme.typography.labelSmall.copy(fontFamily = SCoreDreamFontFamily), color = BodyGray)
                }
                val strokeColor = ChartMint
                val fillTop = ChartMint.copy(alpha = 0.45f)
                val fillBottom = ChartMint.copy(alpha = 0f)
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(160.dp)
                ) {
                    // 그 달에 기록이 1주치 미만이면(주차가 1개뿐이면) 선을 그릴 두 점이 안 나와서 스킵
                    if (weeklyFlow.size < 2) return@Canvas
                    val stepX = size.width / (weeklyFlow.size - 1)
                    // 가장 높은 값이 차트 맨 위까지 꽉 차도록 최댓값 기준으로 정규화
                    val maxValue = weeklyFlow.maxOf { it.value }
                    val points = weeklyFlow.mapIndexed { index, point ->
                        Offset(x = index * stepX, y = size.height * (1 - point.value / maxValue))
                    }
                    val linePath = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(points.last().x, size.height)
                        lineTo(points.first().x, size.height)
                        close()
                    }
                    drawPath(fillPath, brush = Brush.verticalGradient(listOf(fillTop, fillBottom)))
                    drawPath(
                        linePath,
                        color = strokeColor,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    points.forEach { drawCircle(color = strokeColor, radius = 3.dp.toPx(), center = it) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weeklyFlow.forEach { point ->
                    Text(
                        point.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = SCoreDreamFontFamily),
                        color = BodyGray
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmotionDistributionCard(dayColors: List<Color?>) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = SurfaceColor,
        shape = RoundedCornerShape(23.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "감정 분포",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SCoreDreamFontFamily, fontWeight = FontWeight.Bold),
                color = Color.Black
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "이번 달의 감정 흐름을 살펴보세요",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = SCoreDreamFontFamily),
                color = BodyGray
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("월", "화", "수", "목", "금", "토", "일").forEach { dayLabel ->
                    Text(
                        dayLabel,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = SCoreDreamFontFamily),
                        color = BodyGray
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            dayColors.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { cellColor ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                                // 피그마 실측: w=94.358px, h=57.663px (가로가 더 긴 직사각형)
                                .aspectRatio(94.358f / 57.663f)
                                .background(cellColor ?: Color.Transparent, RoundedCornerShape(8.dp))
                        )
                    }
                    repeat(7 - week.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                monthlyEmotionCategories.forEach { category ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(category.color, CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            category.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = SCoreDreamFontFamily),
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopFeltDayCard(
    subtitlePrefix: String,
    dateHighlight: String,
    dateColor: Color,
    description: String,
    bars: List<FeltEmotionBar>
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = SurfaceColor,
        shape = RoundedCornerShape(23.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = buildAnnotatedString {
                    append(subtitlePrefix)
                    withStyle(SpanStyle(color = dateColor, fontWeight = FontWeight.Bold)) {
                        append(dateHighlight)
                    }
                    append("이에요")
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SCoreDreamFontFamily),
                color = Color.Black
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = SCoreDreamFontFamily),
                color = BodyGray
            )
            Spacer(Modifier.height(20.dp))
            val maxPercent = bars.maxOfOrNull { it.percent } ?: 0
            bars.forEach { bar ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        bar.label,
                        modifier = Modifier.width(72.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = SCoreDreamFontFamily),
                        color = Color.Black
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (bar.percent.toFloat() / maxPercent).coerceIn(0.12f, 1f))
                                .fillMaxHeight()
                                .background(bar.color, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                "${bar.percent}%",
                                modifier = Modifier.padding(end = 10.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = SCoreDreamFontFamily, fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonPlaceCard(
    iconRes: Int,
    highlightPrefix: String,
    highlight: String,
    highlightColor: Color,
    recordLine: String,
    feelingPrefix: String,
    feelingHighlight: String,
    feelingColor: Color,
    feelingSuffix: String,
    pills: List<PillStat>
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = SurfaceColor,
        shape = RoundedCornerShape(23.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = buildAnnotatedString {
                        append(highlightPrefix)
                        withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                            append(highlight)
                        }
                        append("이에요")
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SCoreDreamFontFamily),
                    color = Color.Black
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                recordLine,
                modifier = Modifier.padding(start = 36.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = SCoreDreamFontFamily),
                color = BodyGray
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = buildAnnotatedString {
                    append(feelingPrefix)
                    withStyle(SpanStyle(color = feelingColor, fontWeight = FontWeight.Bold)) {
                        append(feelingHighlight)
                    }
                    append(feelingSuffix)
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = SCoreDreamFontFamily),
                color = Color.Black
            )
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pills.forEach { pill ->
                    Row(
                        modifier = Modifier
                            .background(pill.bgColor, RoundedCornerShape(64.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(pill.dotColor, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${pill.label} ${pill.count}회",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = SCoreDreamFontFamily),
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}

private val datePickerItemHeight = 56.dp

// 피그마 "감정리포트_날짜선택"(node 11:961) 팝업 그대로: 연도/월/일 무한 스크롤 휠 피커
// (월간 탭은 일(day) 휠 없이 연도/월만 표시)
@Composable
private fun AnalysisDatePickerDialog(
    initialDate: LocalDate,
    includeDay: Boolean,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var year by remember { mutableStateOf(initialDate.year) }
    var month by remember { mutableStateOf(initialDate.monthValue) }
    var day by remember { mutableStateOf(initialDate.dayOfMonth) }
    val years = remember { ((initialDate.year - 10)..(initialDate.year + 10)).toList() }
    val months = remember { (1..12).toList() }
    val days = remember { (1..31).toList() }

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
                            modifier = Modifier.width(120.dp)
                        ) { item, selected ->
                            DatePickerWheelLabel("${item}년", selected)
                        }
                        WheelPicker(
                            items = months,
                            selectedIndex = month - 1,
                            onSelectedIndexChange = { month = months[it] },
                            itemHeight = datePickerItemHeight,
                            infinite = true,
                            modifier = Modifier.width(if (includeDay) 84.dp else 100.dp)
                        ) { item, selected ->
                            DatePickerWheelLabel("${item}월", selected)
                        }
                        if (includeDay) {
                            WheelPicker(
                                items = days,
                                selectedIndex = day - 1,
                                onSelectedIndexChange = { day = days[it] },
                                itemHeight = datePickerItemHeight,
                                infinite = true,
                                modifier = Modifier.width(84.dp)
                            ) { item, selected ->
                                DatePickerWheelLabel("${item}일", selected)
                            }
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
                        onClick = {
                            val lastDayOfMonth = YearMonth.of(year, month).lengthOfMonth()
                            val safeDay = if (includeDay) day.coerceAtMost(lastDayOfMonth) else 1
                            onConfirm(LocalDate.of(year, month, safeDay))
                        },
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
