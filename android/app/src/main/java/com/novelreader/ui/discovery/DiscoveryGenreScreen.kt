package com.novelreader.ui.discovery

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelreader.narou.model.NarouGenres
import com.novelreader.ui.theme.FontChipLarge
import com.novelreader.ui.theme.FontLabel
import com.novelreader.ui.theme.FontMicroLabel
import com.novelreader.ui.theme.FontScreenTitle
import com.novelreader.ui.theme.MinchoFamily

/**
 * ジャンル一覧画面（モック discovery-genre-D.html）。
 * 大ジャンルごとの一覧と、詳細ジャンルの選択チップをグリッド状に配置する。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiscoveryGenreScreen(
    onBack: () -> Unit,
    onPickBiggenre: (code: Int, label: String) -> Unit,
    onPickGenre: (code: Int, label: String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ジャンルから",
                        fontFamily = MinchoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = FontScreenTitle,
                        letterSpacing = 2.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            NarouGenres.BIGGENRES.forEach { (bigCode, bigLabel) ->
                // セクション見出し行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bigLabel,
                        fontSize = FontMicroLabel,
                        letterSpacing = 3.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "すべて →",
                        fontSize = FontLabel,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onPickBiggenre(bigCode, bigLabel) }
                    )
                }

                // 詳細ジャンルチップ
                val genres = NarouGenres.GENRES_BY_BIG[bigCode] ?: emptyList()
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    genres.forEach { (genreCode, genreLabel) ->
                        // なぜ Box を自作するか: FilterChip は選択状態を持つコンポーネントであり、
                        // ここでは「選択状態を持たず、クリックで即画面遷移するボタン」としてのチップのため、
                        // 意図しない選択アニメーションや色の保持を防ぐために自作が適している。
                        Box(
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(50)
                                )
                                .clip(RoundedCornerShape(50))
                                .clickable { onPickGenre(genreCode, genreLabel) }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = genreLabel,
                                fontSize = FontChipLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            // スクリロール時に最後の下部余白を確保する
            Box(modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}
