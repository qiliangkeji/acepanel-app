package com.acepanel.client.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.data.RootTab
import com.acepanel.client.data.ToastMessage
import com.acepanel.client.ui.theme.AceDimens
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType
import com.acepanel.client.ui.theme.LocalDesktopLayout
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleUser
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Server
import com.composables.icons.lucide.SquareTerminal
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.X
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.SwitchDefaults

@Composable
fun AceText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AceType.body,
    color: Color = AceTheme.colors.text1,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    // 之前默认 TextOverflow.Clip：单行文本放不下时直接从中间硬切一刀，看起来像“文字溢出/被截”。
    // 改成 Ellipsis 后所有 maxLines=1 的调用点（服务器返回的地址、镜像名、备注…）自动带省略号，
    // 多行文本不受影响（只有真正被 maxLines 截断时才会出现省略号）。
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun AceIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = AceTheme.colors.text1,
    size: Dp = 22.dp,
    contentDescription: String? = null,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

@Composable
fun AceCard(
    modifier: Modifier = Modifier,
    padding: Dp = 0.dp,
    cornerRadius: Dp = AceDimens.cardRadius,
    color: Color = AceTheme.colors.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        cornerRadius = cornerRadius,
        insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = color),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(padding), content = content)
    }
}

@Composable
fun AceDivider(modifier: Modifier = Modifier, color: Color = AceTheme.colors.divider) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun CircleAction(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = AceTheme.colors.surface,
    tint: Color = AceTheme.colors.text1,
    size: Dp = AceDimens.headerAction,
    iconSize: Dp = 20.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AceIcon(icon, tint = tint, size = iconSize, contentDescription = contentDescription)
    }
}

@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    leadingIcon: ImageVector? = null,
    loading: Boolean = false,
) {
    val colors = AceTheme.colors
    val bg = if (enabled) colors.accent else colors.fill
    val fg = if (enabled) Color.White else colors.text3
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularDots(color = fg)
            Spacer(Modifier.width(10.dp))
        } else if (leadingIcon != null) {
            AceIcon(leadingIcon, tint = fg, size = 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        AceText(text, style = AceType.button, color = fg, maxLines = 1)
    }
}

@Composable
fun SoftPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    leadingIcon: ImageVector? = null,
    tint: Color = AceTheme.colors.text1,
    background: Color = AceTheme.colors.fill,
) {
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            AceIcon(leadingIcon, tint = tint, size = 17.dp)
            Spacer(Modifier.width(8.dp))
        }
        AceText(text, style = AceType.button.copy(fontSize = 14.sp), color = tint)
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    background: Color,
    modifier: Modifier = Modifier,
    dot: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        }
        // 不给上限的话，一条长状态（例如 “MySQL 主库 · 运行中”）会把同一行里的徽标、
        // 开关、箭头全部挤出卡片右边缘，这里限宽后配合 Ellipsis 自己收缩。
        AceText(
            text,
            style = AceType.tag,
            color = color,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 118.dp),
        )
    }
}

@Composable
fun SoftTag(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(AceTheme.colors.fill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        AceText(
            text,
            style = AceType.tag,
            color = AceTheme.colors.text2,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 132.dp),
        )
    }
}

@Composable
fun MobilePageHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AceDimens.screenPad, end = AceDimens.screenPad, top = 4.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null && !LocalDesktopLayout.current) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                AceIcon(Lucide.ChevronLeft, tint = AceTheme.colors.text1, size = 24.dp)
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            AceText(title, style = AceType.pageTitle, color = AceTheme.colors.text1, maxLines = 2)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                AceText(subtitle, style = AceType.secondary, color = AceTheme.colors.text3, maxLines = 2)
            }
        }
        // 360dp 下：屏幕 360 - 左右留白 52 - 返回键 38 = 270dp 由标题和操作区分。
        // 操作区上限给到 220dp 时标题只剩 50dp，“SSL 证书”只能折行（证书页就是这个现象）。
        // 压到 156dp 并把超出部分做成可横向滚动，标题至少留 114dp；桌面端宽度充裕，放宽到 320dp。
        Row(
            modifier = Modifier
                .padding(start = 6.dp)
                .widthIn(max = if (LocalDesktopLayout.current) 320.dp else 156.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

@Composable
fun AceScreen(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AceTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        content()
    }
}

@Composable
fun MobileBottomNav(
    selected: RootTab,
    onSelect: (RootTab) -> Unit,
) {
    val colors = AceTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        AceDivider(color = colors.divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AceDimens.navHeight)
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            TabItem(RootTab.OVERVIEW, Lucide.LayoutGrid, selected, onSelect, Modifier.weight(1f))
            TabItem(RootTab.SERVERS, Lucide.Server, selected, onSelect, Modifier.weight(1f))
            TabItem(RootTab.TERMINAL, Lucide.SquareTerminal, selected, onSelect, Modifier.weight(1f))
            TabItem(RootTab.MONITOR, Lucide.Activity, selected, onSelect, Modifier.weight(1f))
            TabItem(RootTab.PROFILE, Lucide.CircleUser, selected, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TabItem(
    tab: RootTab,
    icon: ImageVector,
    selected: RootTab,
    onSelect: (RootTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AceTheme.colors
    val active = tab == selected
    Column(
        modifier = modifier
            .clickable { onSelect(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AceIcon(icon, tint = if (active) colors.accent else colors.text3, size = 23.dp)
        AceText(
            tab.label,
            style = AceType.nav.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal),
            color = if (active) colors.accent else colors.text3,
        )
    }
}

@Composable
fun AceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    height: Dp? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = AceType.body.copy(fontSize = 15.sp),
) {
    val colors = AceTheme.colors
    val fieldColors = TextFieldDefaults.textFieldColors(
        backgroundColor = colors.fill,
        labelColor = colors.text3,
        borderColor = colors.accent,
    )
    val h = height ?: AceDimens.inputHeight
    // Miuix 的 TextField 把 leading/trailing 直接塞进 Row，插槽本身不带任何内边距：
    // 只给一个裸图标时，它会贴住胶囊左边缘并与文字叠在一起（登录页、SSH 直连页就是这个现象）。
    // 这里统一给图标一个固定宽度 + 内边距的容器，所有调用点一次性修好。
    val leadingSlot: (@Composable () -> Unit)? = leading?.let { slot ->
        @Composable {
            Box(
                modifier = Modifier
                    .height(h)
                    .widthIn(min = 50.dp)
                    .padding(start = 18.dp, end = 8.dp),
                contentAlignment = Alignment.Center,
            ) { slot() }
        }
    }
    val trailingSlot: (@Composable () -> Unit)? = trailing?.let { slot ->
        @Composable {
            Box(
                modifier = Modifier
                    .height(h)
                    .widthIn(min = 46.dp)
                    .padding(start = 6.dp, end = 16.dp),
                contentAlignment = Alignment.Center,
            ) { slot() }
        }
    }
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = h),
        insideMargin = DpSize(18.dp, 15.dp),
        colors = fieldColors,
        cornerRadius = 999.dp,
        label = placeholder,
        useLabelAsPlaceholder = true,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle.copy(color = colors.text1),
        keyboardOptions = keyboardOptions,
        leadingIcon = leadingSlot,
        trailingIcon = trailingSlot,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
    )
}

@Composable
fun AceSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = AceTheme.colors
    Switch(
        checked = checked,
        modifier = modifier,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.switchColors(
            checkedThumbColor = Color.White,
            uncheckedThumbColor = Color.White,
            checkedTrackColor = colors.accent,
            uncheckedTrackColor = colors.divider,
        ),
    )
}

@Composable
fun AceCheckBox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AceTheme.colors
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (checked) colors.accent else Color.Transparent)
            .border(1.5.dp, if (checked) colors.accent else colors.divider, CircleShape)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            AceIcon(Lucide.Check, tint = Color.White, size = 13.dp)
        }
    }
}

@Composable
fun AceSegmented(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val colors = AceTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(colors.fill)
            .padding(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = selectedIndex == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) colors.surface else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                AceText(
                    label,
                    style = AceType.bodySmall.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (active) colors.text1 else colors.text2,
                )
            }
        }
    }
}

@Composable
fun ProgressLine(
    percent: Double,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 5.dp,
    track: Color = AceTheme.colors.divider,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth((percent / 100.0).coerceIn(0.0, 1.0).toFloat())
                .height(height)
                .clip(RoundedCornerShape(999.dp))
                .background(color),
        )
    }
}

@Composable
fun SectionTitle(text: String, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AceText(text, style = AceType.section, color = AceTheme.colors.text1, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun InlineNotice(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AceTheme.colors.warning,
    background: Color = AceTheme.colors.warningSoft,
    icon: ImageVector = Lucide.TriangleAlert,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        AceIcon(icon, tint = color, size = 17.dp)
        AceText(text, style = AceType.secondary, color = color, modifier = Modifier.weight(1f))
    }
}

@Composable
fun InfoNotice(text: String, modifier: Modifier = Modifier) {
    InlineNotice(
        text = text,
        modifier = modifier,
        color = AceTheme.colors.accent,
        background = AceTheme.colors.accentSoft,
        icon = Lucide.CircleAlert,
    )
}

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    rows: Int = 3,
) {
    val colors = AceTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(rows) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface),
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Lucide.LayoutGrid,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(AceTheme.colors.fill),
            contentAlignment = Alignment.Center,
        ) {
            AceIcon(icon, tint = AceTheme.colors.text3, size = 26.dp)
        }
        Spacer(Modifier.height(14.dp))
        AceText(title, style = AceType.listStrong, color = AceTheme.colors.text1, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        AceText(
            subtitle,
            style = AceType.secondary,
            color = AceTheme.colors.text3,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            SoftPillButton(actionText, onAction, height = 40.dp)
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(AceTheme.colors.errorSoft),
            contentAlignment = Alignment.Center,
        ) {
            AceIcon(Lucide.CircleAlert, tint = AceTheme.colors.error, size = 26.dp)
        }
        Spacer(Modifier.height(14.dp))
        AceText("加载失败", style = AceType.listStrong, color = AceTheme.colors.text1)
        Spacer(Modifier.height(6.dp))
        AceText(message, style = AceType.secondary, color = AceTheme.colors.text3, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        SoftPillButton("重新加载", onRetry, height = 40.dp, leadingIcon = Lucide.RefreshCw)
    }
}

@Composable
fun CircularDots(color: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.25f)),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .border(2.dp, color, CircleShape),
        )
    }
}

@Composable
fun ToastHost(message: ToastMessage?, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier.fillMaxSize(),
    ) {
        val current = message ?: return@AnimatedVisibility
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .padding(bottom = 96.dp, start = 24.dp, end = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AceTheme.colors.text1.copy(alpha = 0.94f))
                    .clickable { onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AceIcon(
                    if (current.isError) Lucide.CircleAlert else Lucide.CircleCheck,
                    tint = if (current.isError) AceTheme.colors.error else AceTheme.colors.success,
                    size = 17.dp,
                )
                AceText(
                    current.text,
                    style = AceType.bodySmall,
                    color = Color.White,
                    maxLines = 3,
                    // 长报错（面板返回的整段 HTML / 超时信息）以前会直接把 Toast 撑出屏幕，
                    // fill=false 只在放不下时收缩，短提示仍然按内容自适应宽度。
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

@Composable
fun RowChevron(tint: Color = AceTheme.colors.text3) {
    AceIcon(Lucide.ChevronRight, tint = tint, size = 17.dp)
}

@Composable
fun IconBadge(color: Color, size: Dp = 7.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun StatusDot(active: Boolean, size: Dp = 8.dp) {
    IconBadge(if (active) AceTheme.colors.success else AceTheme.colors.text3, size)
}

@Composable
fun CloseCircle(onClick: () -> Unit) {
    CircleAction(Lucide.X, onClick, background = AceTheme.colors.fill, tint = AceTheme.colors.text2, size = 34.dp, iconSize = 17.dp)
}


@Composable
fun AceDialog(
    title: String? = null,
    onDismiss: () -> Unit,
    // 操作菜单这类条目很多的弹窗需要自身可滚动，否则在小屏上会被窗口裁掉
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(180),
        label = "dialog-enter",
    )
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .widthIn(max = 420.dp)
                .then(if (scrollable) Modifier.heightIn(max = 620.dp) else Modifier)
                .graphicsLayer {
                    alpha = progress
                    scaleX = 0.94f + 0.06f * progress
                    scaleY = 0.94f + 0.06f * progress
                }
                .clip(RoundedCornerShape(24.dp))
                .background(AceTheme.colors.surface)
                .padding(22.dp),
        ) {
            if (title != null) {
                AceText(title, style = AceType.section.copy(fontSize = 18.sp), color = AceTheme.colors.text1)
                Spacer(Modifier.height(12.dp))
            }
            if (scrollable) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    content = content,
                )
            } else {
                content()
            }
        }
    }
}
