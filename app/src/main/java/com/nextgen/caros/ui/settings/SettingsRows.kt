package com.nextgen.caros.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableStateListOf
import com.nextgen.caros.ui.components.Chip
import com.nextgen.caros.ui.components.GlassPanel
import com.nextgen.caros.ui.components.PillButton
import com.nextgen.caros.ui.components.SectionLabel
import com.nextgen.caros.ui.components.pressable
import com.nextgen.caros.ui.theme.LocalNg
import com.nextgen.caros.ui.theme.NgType

@Composable
fun SettingGroup(title: String, footer: String? = null, content: @Composable () -> Unit) {
    val ng = LocalNg.current
    Column(Modifier.fillMaxWidth()) {
        Text(title.uppercase(), style = NgType.caption.copy(color = ng.textSecondary, letterSpacing = 0.3.sp), modifier = Modifier.padding(start = 18.dp, bottom = 7.dp))
        DividedColumn(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(if (ng.isDark) Color(0xFF1C1C1E).copy(alpha = 0.94f) else Color.White),
            divider = if (ng.isDark) Color(0xFF38383A) else Color(0xFFC6C6C8),
            content = content,
        )
        if (footer != null) Text(footer, style = NgType.caption.copy(color = ng.textSecondary, letterSpacing = 0.sp), modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 7.dp))
        Spacer(Modifier.height(24.dp))
    }
}

/** Vertical stack that draws iOS hairline separators (inset from the leading edge) between rows. */
@Composable
private fun DividedColumn(modifier: Modifier, divider: Color, content: @Composable () -> Unit) {
    // Plain holder (not state): written during layout, read during the draw that follows it.
    val lines = remember { ArrayList<Int>() }
    Layout(
        content = content,
        modifier = modifier.drawWithContent {
            drawContent()
            lines.forEach { y -> drawLine(divider, Offset(16.dp.toPx(), y.toFloat()), Offset(size.width, y.toFloat()), strokeWidth = 1f) }
        },
    ) { measurables, constraints ->
        val ps = measurables.map { it.measure(constraints.copy(minHeight = 0)) }.filter { it.height > 0 }
        val h = ps.sumOf { it.height }
        val ys = mutableListOf<Int>()
        layout(constraints.maxWidth, h) {
            var y = 0
            ps.forEachIndexed { i, p -> p.place(0, y); y += p.height; if (i < ps.size - 1) ys += y }
            lines.clear(); lines.addAll(ys)
        }
    }
}

@Composable
private fun RowText(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    val ng = LocalNg.current
    Column(modifier) {
        Text(title, style = NgType.body.copy(color = ng.textPrimary))
        if (subtitle != null) Text(subtitle, style = NgType.caption.copy(color = ng.textSecondary))
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    val ng = LocalNg.current
    Row(
        Modifier.fillMaxWidth().pressable(pressedScale = 0.99f) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        com.nextgen.caros.ui.components.IosSwitch(checked, onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(title: String, subtitle: String? = null, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        RowText(title, subtitle)
        Spacer(Modifier.height(10.dp))
        if (options.size in 2..4 && options.all { it.second.length <= 18 }) {
            com.nextgen.caros.ui.components.SegmentedControl(options, selected, onSelect, Modifier.fillMaxWidth().widthIn(max = 620.dp))
        } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (v, label) -> Chip(label, selected = v == selected) { onSelect(v) } }
        }
    }
}

@Composable
fun SliderRow(
    title: String, value: Int, range: IntRange, step: Int = 1, valueLabel: (Int) -> String,
    subtitle: String? = null, onChange: (Int) -> Unit,
) {
    val ng = LocalNg.current
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowText(title, subtitle, Modifier.weight(1f))
            Text(valueLabel(local.toInt()), style = NgType.label.copy(color = ng.accent))
        }
        Slider(
            value = local,
            onValueChange = { local = (Math.round(it / step) * step).toFloat() },
            onValueChangeFinished = { onChange(local.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = ng.accent, inactiveTrackColor = ng.textPrimary.copy(alpha = 0.15f)),
        )
    }
}

@Composable
fun TextFieldRow(title: String, subtitle: String? = null, value: String, placeholder: String, onSave: (String) -> Unit, button: String = "Save") {
    val ng = LocalNg.current
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        RowText(title, subtitle)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(ng.glass).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (text.isEmpty()) Text(placeholder, style = NgType.body.copy(color = ng.textTertiary))
                BasicTextField(text, { text = it }, singleLine = true, textStyle = NgType.body.copy(color = ng.textPrimary),
                    cursorBrush = SolidColor(ng.accent), modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.width(10.dp))
            PillButton(button, primary = text != value, enabled = text != value) { onSave(text) }
        }
    }
}

@Composable
fun ActionRow(title: String, subtitle: String? = null, button: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RowText(title, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        PillButton(button, primary = primary, enabled = enabled, onClick = onClick)
    }
}

@Composable
fun InfoRow(title: String, body: String, accent: Color? = null) {
    val ng = LocalNg.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).widthIn(max = 900.dp)) {
        Text(title, style = NgType.label.copy(color = accent ?: ng.textPrimary))
        Text(body, style = NgType.caption.copy(color = ng.textSecondary))
    }
}
