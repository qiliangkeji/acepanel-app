package com.acepanel.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acepanel.client.ui.theme.AceTheme
import com.acepanel.client.ui.theme.AceType

data class AceFormField(
    val label: String,
    val initial: String = "",
    val password: Boolean = false,
    val numeric: Boolean = false,
    val multiline: Boolean = false,
)

@Composable
fun AceFormDialog(
    title: String,
    fields: List<AceFormField>,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val colors = AceTheme.colors
    val values = remember { mutableStateListOf<String>().also { list -> list.addAll(fields.map { it.initial }) } }
    AceDialog(title = title, onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            fields.forEachIndexed { index, field ->
                if (field.multiline) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.fill)
                            .padding(12.dp),
                    ) {
                        if (values.getOrElse(index) { "" }.isEmpty()) {
                            AceText(field.label, style = AceType.caption.copy(fontFamily = FontFamily.Monospace), color = colors.text3)
                        }
                        BasicTextField(
                            value = values.getOrElse(index) { "" },
                            onValueChange = { values[index] = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                            textStyle = AceType.caption.copy(
                                fontFamily = FontFamily.Monospace,
                                color = colors.text1,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            ),
                            cursorBrush = SolidColor(colors.accent),
                        )
                    }
                } else {
                    AceTextField(
                        value = values.getOrElse(index) { "" },
                        onValueChange = { values[index] = it },
                        placeholder = field.label,
                        height = 46.dp,
                        textStyle = AceType.bodySmall,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (field.numeric) KeyboardType.Number else KeyboardType.Text,
                        ),
                        visualTransformation = if (field.password) PasswordVisualTransformation() else VisualTransformation.None,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftPillButton("取消", onDismiss, modifier = Modifier.weight(1f), height = 44.dp)
            Box(Modifier.weight(1f)) {
                PrimaryPillButton(confirmText, { onConfirm(values.toList()) }, height = 44.dp)
            }
        }
    }
}

@Composable
fun AceConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    danger: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = AceTheme.colors
    AceDialog(title = title, onDismiss = onDismiss) {
        AceText(message, style = AceType.bodySmall, color = colors.text2)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftPillButton("取消", onDismiss, modifier = Modifier.weight(1f), height = 44.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (danger) colors.error else colors.accent)
                    .clickable(onClick = onConfirm),
                contentAlignment = Alignment.Center,
            ) {
                AceText(confirmText, style = AceType.button.copy(fontSize = 14.sp), color = Color.White)
            }
        }
    }
}
