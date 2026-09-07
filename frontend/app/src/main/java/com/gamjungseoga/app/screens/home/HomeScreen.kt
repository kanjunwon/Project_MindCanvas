package com.gamjungseoga.app.screens.home

import com.gamjungseoga.app.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlin.math.abs
import com.gamjungseoga.app.network.DiaryEntry
import com.gamjungseoga.app.screens.diary.DiaryListState
import com.gamjungseoga.app.screens.diary.DiaryListViewModel
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.gamjungseoga.app.ui.theme.AccentBlue
import com.gamjungseoga.app.ui.theme.AccentGreen
import com.gamjungseoga.app.ui.theme.AccentOrange
import com.gamjungseoga.app.ui.theme.CardBackground
import com.gamjungseoga.app.ui.theme.CountLabelBrown
import com.gamjungseoga.app.ui.theme.FoldBackGray
import com.gamjungseoga.app.ui.theme.GamjeongseogaTheme
import com.gamjungseoga.app.ui.theme.HeaderGreen
import com.gamjungseoga.app.ui.theme.HeaderTextOnGreen
import com.gamjungseoga.app.ui.theme.MonthLabelGray
import com.gamjungseoga.app.ui.theme.PlaceholderNavy
import com.gamjungseoga.app.ui.theme.PlaceholderOcean
import com.gamjungseoga.app.ui.theme.PlaceholderTerracotta
import com.gamjungseoga.app.ui.theme.RibbonPink
import com.gamjungseoga.app.ui.theme.SolidGreen
import com.gamjungseoga.app.ui.theme.TitleBrown

// imageUrl: 백엔드(SD3)가 생성해주는 감정 이미지 URL. 있으면 우선 사용, 없으면 imageRes(샘플 일러스트) 표시
private data class MonthlyEmotion(
    val month: String,
    val emotion: String,
    val count: String,
    val imageRes: Int? = null,
    val imageUrl: String? = null
)

private data class DiaryPage(
    val dateTop: String,
    val dateBottom: String,
    val color: Color,
    val imageUrl: String? = null,
    val showDateTag: Boolean = false
)

// SD3가 달마다 대표 이미지를 만들어주는 API가 아직 없어서, 카드 장식(마스킹테이프 등)과 마찬가지로
// 이미지는 이 3장을 순서대로 돌려씀. month/emotion/count만 실제 일기 목록에서 계산.
private val monthlyEmotionImageRes = listOf(R.drawable.emotion_card_1, R.drawable.emotion_card_2, R.drawable.emotion_card_3)

// 이번 달 포함 최근 3개월 각각에 대해, 그 달 일기들의 top_emotion 중 가장 많이 나온 감정을
// 대표 감정으로 삼음(최빈값). GET /diaries/{userId}가 이미 각 일기의 top_emotion/created_at을
// 내려주고 있어서 백엔드 추가 연동 없이 프론트에서 바로 집계 가능.
private fun computeMonthlyEmotions(diaries: List<DiaryEntry>): List<MonthlyEmotion> {
    val entriesByMonth = diaries
        .mapNotNull { entry ->
            val created = entry.createdAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
            created?.let { YearMonth.from(it) to entry }
        }
        .groupBy({ it.first }, { it.second })

    val currentMonth = YearMonth.now()
    return (0..2).map { monthsAgo ->
        val yearMonth = currentMonth.minusMonths(monthsAgo.toLong())
        val topEmotionCounts = entriesByMonth[yearMonth].orEmpty()
            .mapNotNull { it.topEmotion }
            .groupingBy { it }
            .eachCount()
        val topEmotion = topEmotionCounts.maxByOrNull { it.value }

        MonthlyEmotion(
            month = "${yearMonth.monthValue}월의 감정",
            emotion = topEmotion?.key ?: "기록 없음",
            count = "${topEmotion?.value ?: 0}회 기록",
            imageRes = monthlyEmotionImageRes.getOrNull(monthsAgo)
        )
    }
}

private val recentPageColors = listOf(PlaceholderOcean, PlaceholderNavy, PlaceholderTerracotta)
private val recentPageDateFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

// SD3 생성 이미지(imageUrl)가 아직 없어서, 실제 일기 목록도 색상만 순환시켜 카드로 표시.
private fun DiaryEntry.toDiaryPage(index: Int): DiaryPage {
    val created = createdAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
    return DiaryPage(
        dateTop = created?.format(recentPageDateFormatter)?.uppercase(Locale.ENGLISH) ?: "-",
        dateBottom = created?.dayOfMonth?.toString() ?: "-",
        color = recentPageColors[index % recentPageColors.size],
        showDateTag = true
    )
}

// 가운데(현재 페이지) 기준 크기, 옆 페이지는 이 크기에서 축소됨
private const val PAGE_CARD_MIN_SCALE = 0.73f
private const val PAGE_CARD_MAX_SCALE = 1f
private val PAGE_CARD_BASE_WIDTH = 258.dp
private val PAGE_CARD_BASE_HEIGHT = 354.dp
private val PAGE_CARD_RIBBON_OVERHANG = 6.dp

// 책갈피(리본) 모양: 위/양옆은 각지고, 아래쪽 가운데가 V자로 파인 형태
private class BookmarkRibbonShape(private val notchHeight: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val notchPx = with(density) { notchHeight.toPx() }
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(size.width / 2f, size.height - notchPx)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

// LazyRow 스크롤 위치를 기준으로 화면 중앙에 가까울수록 1(확대), 멀수록 0(축소)에 가까운 값 계산
private fun centerScale(listState: LazyListState, index: Int): Float {
    val layoutInfo = listState.layoutInfo
    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        ?: return PAGE_CARD_MIN_SCALE
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val itemCenter = itemInfo.offset + itemInfo.size / 2f
    val distance = abs(itemCenter - viewportCenter)
    val maxDistance = layoutInfo.viewportSize.width / 2f
    val fraction = (1f - (distance / maxDistance)).coerceIn(0f, 1f)
    return PAGE_CARD_MIN_SCALE + (PAGE_CARD_MAX_SCALE - PAGE_CARD_MIN_SCALE) * fraction
}

@Composable
fun HomeScreen(diaryListViewModel: DiaryListViewModel = viewModel()) {
    val listState = diaryListViewModel.state
    val recentPages = remember(listState) {
        (listState as? DiaryListState.Loaded)?.diaries
            ?.take(6)
            ?.mapIndexed { index, entry -> entry.toDiaryPage(index) }
            .orEmpty()
    }

    // 배경(바탕색+텍스처)은 MainActivity의 전체 배경 레이어가 담당하므로 여기서는 따로 안 채움
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { HomeHeader() }

        item {
            Column(modifier = Modifier.padding(top = 24.dp)) {
                SectionTitle("나의 감정")
                Spacer(Modifier.height(16.dp))
                when {
                    listState is DiaryListState.Loading -> Text(
                        "불러오는 중...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MonthLabelGray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    listState is DiaryListState.Error -> Text(
                        "감정 기록을 불러오지 못했어요. (${listState.message})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MonthLabelGray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    else -> {
                        val monthlyEmotions = remember(listState) {
                            computeMonthlyEmotions((listState as DiaryListState.Loaded).diaries)
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            itemsIndexed(monthlyEmotions) { index, item ->
                                MonthlyEmotionCard(item, index)
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(top = 28.dp, bottom = 20.dp)) {
                SectionTitle("최근 작성한 페이지")
                Spacer(Modifier.height(12.dp))
                when {
                    listState is DiaryListState.Loading -> Text(
                        "불러오는 중...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MonthLabelGray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    listState is DiaryListState.Error -> Text(
                        "일기 목록을 불러오지 못했어요. (${listState.message})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MonthLabelGray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    recentPages.isEmpty() -> Text(
                        "아직 작성한 일기가 없어요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MonthLabelGray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    else -> {
                        val pagesListState = rememberLazyListState()
                        val density = LocalDensity.current
                        BoxWithConstraints {
                            val viewportWidthPx = with(density) { maxWidth.toPx() }
                            val centerCardWidthPx = with(density) { PAGE_CARD_BASE_WIDTH.toPx() }
                            // 화면 로드 시 가운데(현재 페이지, index 1)가 뷰포트 정중앙에 오도록 초기 스크롤 위치 계산
                            LaunchedEffect(recentPages) {
                                val centerIndex = (recentPages.size - 1).coerceAtMost(1)
                                val offsetPx = ((viewportWidthPx - centerCardWidthPx) / 2f).toInt()
                                pagesListState.scrollToItem(index = centerIndex, scrollOffset = -offsetPx)
                            }
                            LazyRow(
                                state = pagesListState,
                                contentPadding = PaddingValues(horizontal = 40.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(recentPages) { index, page ->
                                    DiaryPageCard(page, index, pagesListState)
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
private fun HomeHeader() {
    // 헤더 배경: 반투명 초록 사각형(rectangle_1) 위에, 날짜/문구/일러스트/접힌 모서리를 겹쳐서 배치
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 178.dp)
            .background(HeaderGreen)
    ) {
        // 우측 하단 접힌 종이 모서리 (header_fold_accent.png 에셋이 투명 배경 없이 흰색으로
        // 내보내져 있어서 흰 삼각형이 비쳐보이는 문제가 있었음 -> 피그마 벡터 좌표 그대로 직접 그림)
        HeaderFoldAccent(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(width = 33.dp, height = 26.dp)
        )

        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 49.dp, bottom = 20.dp)) {
            // TODO: 오늘 날짜(시스템 날짜)로 교체
            Text("2026.06.04", color = HeaderTextOnGreen, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text("오늘의 감정을", color = HeaderTextOnGreen, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text("한 페이지로 남겨보세요", color = HeaderTextOnGreen, style = MaterialTheme.typography.headlineSmall)
        }

        // 우측상단 일러스트 (res/drawable/header_illustration.png)
        Image(
            painter = painterResource(R.drawable.header_illustration),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 57.dp, end = 16.dp)
                .size(width = 92.dp, height = 104.dp)
        )
    }
}

// 피그마 벡터(node 1:118) 좌표 그대로: 뒷면(연한 회색) 삼각형 위에 앞면(진한 초록) 삼각형을 겹쳐
// 종이 모서리가 접힌 듯한 효과를 냄. 87x69 기준 좌표를 0~1 비율로 변환해서 그림.
@Composable
private fun HeaderFoldAccent(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val backPath = Path().apply {
            moveTo(w, h)
            lineTo(0f, h)
            lineTo(w, h * (9f / 69f))
            close()
        }
        drawPath(backPath, color = FoldBackGray)

        val frontPath = Path().apply {
            moveTo(w * (13.1818f / 87f), 0f)
            lineTo(w, h * (9.11322f / 69f))
            lineTo(0f, h)
            close()
        }
        drawPath(frontPath, color = SolidGreen)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = TitleBrown,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun MonthlyEmotionCard(item: MonthlyEmotion, index: Int) {
    Box(modifier = Modifier.size(114.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CardBackground)
                .padding(start = 7.dp, top = 8.dp, end = 7.dp)
        ) {
            // 사진 영역: imageUrl(SD3 생성 결과) 있으면 그걸, 없으면 샘플 일러스트, 둘 다 없으면 배경만
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(65.dp)
            ) {
                if (item.imageUrl != null) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.emotion,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (item.imageRes != null) {
                    Image(
                        painter = painterResource(item.imageRes),
                        contentDescription = item.emotion,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(item.month, style = MaterialTheme.typography.labelSmall, color = MonthLabelGray)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(item.emotion, style = MaterialTheme.typography.titleSmall, color = TitleBrown)
                Spacer(Modifier.width(4.dp))
                Text(item.count, style = MaterialTheme.typography.labelSmall, color = CountLabelBrown)
            }
        }

        // 카드마다 다른 장식 (마스킹테이프/스티커), 피그마 디자인 그대로
        when (index) {
            0 -> OrangeTapeAccent()
            1 -> BlueCornerAccents()
            2 -> GreenDotAccent()
        }
    }
}

@Composable
private fun BoxScope.OrangeTapeAccent() {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = (-4).dp)
            .size(width = 34.dp, height = 8.dp)
            .background(AccentOrange, RoundedCornerShape(2.dp))
    )
}

@Composable
private fun BoxScope.BlueCornerAccents() {
    // 피그마 실측(9.9x57.6px, rotate 39.94deg): 폭 넓은 막대가 아니라 가늘고 긴 조각이
    // 40도 정도 기울어 모서리에 살짝 걸쳐있는 모양(리본/종이 접힘 느낌)
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = 2.dp, y = (-6).dp)
            .size(width = 4.dp, height = 22.dp)
            .graphicsLayer { rotationZ = 40f }
            .background(AccentBlue, RoundedCornerShape(1.dp))
    )
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = (-2).dp, y = 6.dp)
            .size(width = 4.dp, height = 22.dp)
            .graphicsLayer { rotationZ = 40f }
            .background(AccentBlue, RoundedCornerShape(1.dp))
    )
}

@Composable
private fun BoxScope.GreenDotAccent() {
    // 피그마 기준 카드 위쪽 가운데, 상단 경계에 걸치는 위치
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = (-7).dp)
            .size(14.dp)
            .background(AccentGreen, CircleShape)
    )
}

@Composable
private fun DiaryPageCard(page: DiaryPage, index: Int, listState: LazyListState) {
    // 실제 레이아웃 크기 자체를 바꿔서 이웃 카드가 가려지지 않고 옆으로 밀려나게 함
    val scale = centerScale(listState, index)
    val cardWidth = PAGE_CARD_BASE_WIDTH * scale
    val cardHeight = PAGE_CARD_BASE_HEIGHT * scale

    // 바깥 Box에 책갈피가 튀어나올 여유 공간(overhang)을 위쪽에 미리 확보해둠 (LazyRow가 넘치는 부분을 잘라내는 것 방지)
    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight + PAGE_CARD_RIBBON_OVERHANG)
    ) {
        // 실제로 보이는 다이어리 페이지 박스(사진/색상). 바깥 Box 하단에 붙여서
        // 위쪽 overhang 공간은 비워두고 책갈피가 그 공간에 걸치도록 함
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(cardHeight)
                .background(page.color)
        ) {
            // 백엔드가 이미지 URL을 주면 SD3 생성 이미지로, 없으면 위 color가 그대로 보임
            if (page.imageUrl != null) {
                AsyncImage(
                    model = page.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (page.showDateTag) {
            // 오른쪽 위 모서리에서 살짝 왼쪽으로, 박스 위쪽 경계보다 위로 튀어나오게 배치
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-18).dp)
                    .graphicsLayer {
                        // 카드와 같은 비율로 리본도 커지고 작아짐 (오른쪽 위 모서리 기준으로 확대/축소)
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(1f, 0f)
                    },
                color = RibbonPink,
                shape = BookmarkRibbonShape(notchHeight = 10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp).padding(bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(page.dateTop, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(page.dateBottom, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun HomeScreenPreview() {
    GamjeongseogaTheme {
        HomeScreen()
    }
}
