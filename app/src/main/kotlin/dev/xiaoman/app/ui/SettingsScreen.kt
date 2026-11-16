package dev.xiaoman.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.xiaoman.api.ModelRole
import dev.xiaoman.app.AppGraph
import dev.xiaoman.app.ModelTestResult
import dev.xiaoman.app.R
import dev.xiaoman.app.settings.SettingsForm
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(graph: AppGraph) {
    val store = graph.settings
    var form by remember { mutableStateOf(SettingsForm.of(store.current)) }
    var saved by remember { mutableStateOf(false) }
    val parsed = form.toSettings()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)

        ModelSection(stringResource(R.string.settings_chat_model), form.chat, ModelRole.CHAT, graph) {
            form = form.copy(chat = it)
            saved = false
        }
        HorizontalDivider()
        ModelSection(stringResource(R.string.settings_operator_model), form.operator, ModelRole.OPERATOR, graph) {
            form = form.copy(operator = it)
            saved = false
        }
        TextButton(onClick = {
            form = form.copy(operator = form.chat.copy())
            saved = false
        }) { Text(stringResource(R.string.settings_copy_chat)) }
        HorizontalDivider()

        Text(stringResource(R.string.settings_agent), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = form.maxSteps,
            onValueChange = {
                form = form.copy(maxSteps = it)
                saved = false
            },
            label = { Text(stringResource(R.string.settings_max_steps)) },
            isError = !form.maxStepsValid,
            supportingText = { if (!form.maxStepsValid) Text(stringResource(R.string.settings_invalid_number)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_learn), modifier = Modifier.weight(1f))
            Switch(checked = form.learnFromSuccess, onCheckedChange = {
                form = form.copy(learnFromSuccess = it)
                saved = false
            })
        }

        HorizontalDivider()
        OverlayTransparencyControl(form.overlayTransparency) {
            form = form.copy(overlayTransparency = it)
            saved = false
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = parsed != null, onClick = {
                parsed?.let {
                    store.save(it)
                    saved = true
                }
            }) { Text(stringResource(R.string.settings_save)) }
            if (saved) {
                Text(
                    stringResource(R.string.settings_saved),
                    modifier = Modifier.padding(start = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

        HorizontalDivider()
        Text(stringResource(R.string.settings_folders), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_folder_skills, graph.skillsDir.path), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.settings_folder_memory, graph.memoryDir.path), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.settings_folder_traces, graph.tracesDir.path), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ModelSection(
    title: String,
    model: SettingsForm.ModelForm,
    role: ModelRole,
    graph: AppGraph,
    onChange: (SettingsForm.ModelForm) -> Unit,
) {
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ModelTestResult?>(null) }
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(
            enabled = !testing && model.toSettings() != null,
            onClick = {
                val target = model.toSettings() ?: return@TextButton
                testing = true
                result = null
                scope.launch {
                    result = graph.testModel(target, role)
                    testing = false
                }
            },
        ) {
            Text(stringResource(if (testing) R.string.settings_test_model_running else R.string.settings_test_model))
        }
    }
    result?.let {
        Text(
            if (it.ok) stringResource(R.string.settings_test_ok, it.elapsedMillis, it.detail)
            else stringResource(R.string.settings_test_failed, it.detail),
            style = MaterialTheme.typography.bodySmall,
            color = if (it.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
    OutlinedTextField(
        value = model.baseUrl,
        onValueChange = { onChange(model.copy(baseUrl = it)) },
        label = { Text(stringResource(R.string.settings_base_url)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = model.model,
        onValueChange = { onChange(model.copy(model = it)) },
        label = { Text(stringResource(R.string.settings_model)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = model.apiKey,
        onValueChange = { onChange(model.copy(apiKey = it)) },
        label = { Text(stringResource(R.string.settings_api_key)) },
        singleLine = true,
        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { showKey = !showKey }) {
                Icon(
                    if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(if (showKey) R.string.settings_hide_key else R.string.settings_show_key),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = model.temperature,
        onValueChange = { onChange(model.copy(temperature = it)) },
        label = { Text(stringResource(R.string.settings_temperature)) },
        isError = !model.temperatureValid,
        supportingText = { if (!model.temperatureValid) Text(stringResource(R.string.settings_invalid_number)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun OverlayTransparencyControl(transparency: Int, onChange: (Int) -> Unit) {
    val label = stringResource(R.string.settings_overlay_transparency)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("$transparency%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = transparency.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
        Text(stringResource(R.string.settings_overlay_transparency_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OverlayPreview(transparency)
    }
}

/** A miniature third-party screen with the step pill on top, reflecting the chosen transparency live. */
@Composable
private fun OverlayPreview(transparency: Int) {
    val alpha = (100 - transparency).coerceIn(0, 100) / 100f
    Box(
        modifier = Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFE8F0FE), Color(0xFFFCE8E6), Color(0xFFE6F4EA)))),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.fillMaxWidth(0.72f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFB0BEC5)))
            Box(Modifier.fillMaxWidth(0.9f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFCFD8DC)))
            Box(Modifier.fillMaxWidth(0.55f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFCFD8DC)))
        }
        Row(
            modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1E4635).copy(alpha = alpha)).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.settings_overlay_preview_step), color = Color.White,
                style = MaterialTheme.typography.labelMedium.copy(shadow = Shadow(Color(0xB3000000), Offset(0f, 1f), 2f)),
                maxLines = 1,
            )
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF2E7D32)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text(stringResource(R.string.settings_overlay_preview_stop), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
