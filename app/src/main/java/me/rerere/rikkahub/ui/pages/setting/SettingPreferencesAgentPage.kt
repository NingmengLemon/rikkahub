package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupValue
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

private const val TOOL_TIMEOUT_MIN_MINUTES = 1
private const val TOOL_TIMEOUT_MAX_MINUTES = 30
private const val TOOL_TIMEOUT_DEFAULT_SECONDS = 300
private const val SHELL_MAX_MAX_SECONDS = 3600

@Composable
fun SettingPreferencesAgentPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val agent = settings.agentSetting
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val toolMinutes = (agent.toolTimeoutSeconds / 60).coerceIn(TOOL_TIMEOUT_MIN_MINUTES, TOOL_TIMEOUT_MAX_MINUTES)

    fun updateToolTimeout(seconds: Int) = vm.updateSettings {
        it.copy(agentSetting = it.agentSetting.copy(toolTimeoutSeconds = seconds))
    }

    fun updateShellDefault(seconds: Int) = vm.updateSettings {
        val max = it.agentSetting.shellMaxTimeoutSeconds
        it.copy(
            agentSetting = it.agentSetting.copy(
                shellDefaultTimeoutSeconds = seconds.coerceIn(1, max),
            ),
        )
    }

    fun updateShellMax(seconds: Int) = vm.updateSettings {
        val max = seconds.coerceIn(60, SHELL_MAX_MAX_SECONDS)
        it.copy(
            agentSetting = it.agentSetting.copy(
                shellMaxTimeoutSeconds = max,
                shellDefaultTimeoutSeconds = it.agentSetting.shellDefaultTimeoutSeconds.coerceAtMost(max),
            ),
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_page_preferences_agent)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(title = { Text(stringResource(R.string.setting_page_agent_tool_group)) }) {
                    switchItem(
                        checked = agent.toolTimeoutSeconds > 0,
                        onCheckedChange = { enabled ->
                            updateToolTimeout(if (enabled) TOOL_TIMEOUT_DEFAULT_SECONDS else 0)
                        },
                        supportingContent = {
                            Text(stringResource(R.string.setting_page_agent_tool_timeout_desc))
                        },
                        headlineContent = {
                            Text(stringResource(R.string.setting_page_agent_tool_timeout))
                        },
                    )
                    if (agent.toolTimeoutSeconds > 0) {
                        formItem(
                            headlineContent = {
                                Text(stringResource(R.string.setting_page_agent_tool_timeout_value))
                            },
                            trailingContent = {
                                CardGroupValue(
                                    stringResource(R.string.setting_page_agent_minutes, toolMinutes)
                                )
                            },
                        ) {
                            Slider(
                                value = toolMinutes.toFloat(),
                                onValueChange = { value ->
                                    val minutes = value.roundToInt()
                                        .coerceIn(TOOL_TIMEOUT_MIN_MINUTES, TOOL_TIMEOUT_MAX_MINUTES)
                                    if (minutes * 60 != agent.toolTimeoutSeconds) {
                                        updateToolTimeout(minutes * 60)
                                    }
                                },
                                valueRange = TOOL_TIMEOUT_MIN_MINUTES.toFloat()..TOOL_TIMEOUT_MAX_MINUTES.toFloat(),
                                steps = TOOL_TIMEOUT_MAX_MINUTES - TOOL_TIMEOUT_MIN_MINUTES - 1,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            item {
                CardGroup(title = { Text(stringResource(R.string.setting_page_agent_shell_group)) }) {
                    formItem(
                        headlineContent = {
                            Text(stringResource(R.string.setting_page_agent_shell_default))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.setting_page_agent_shell_default_desc))
                        },
                        trailingContent = {
                            CardGroupValue(
                                stringResource(
                                    R.string.setting_page_agent_seconds,
                                    agent.shellDefaultTimeoutSeconds,
                                )
                            )
                        },
                    ) {
                        Slider(
                            value = agent.shellDefaultTimeoutSeconds
                                .coerceAtMost(agent.shellMaxTimeoutSeconds)
                                .toFloat(),
                            onValueChange = { value ->
                                val seconds = (value / 5).roundToInt() * 5
                                if (seconds != agent.shellDefaultTimeoutSeconds) {
                                    updateShellDefault(seconds)
                                }
                            },
                            valueRange = 5f..agent.shellMaxTimeoutSeconds.toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    formItem(
                        headlineContent = {
                            Text(stringResource(R.string.setting_page_agent_shell_max))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.setting_page_agent_shell_max_desc))
                        },
                        trailingContent = {
                            CardGroupValue(
                                stringResource(R.string.setting_page_agent_seconds, agent.shellMaxTimeoutSeconds)
                            )
                        },
                    ) {
                        Slider(
                            value = agent.shellMaxTimeoutSeconds.toFloat(),
                            onValueChange = { value ->
                                val seconds = (value / 30).roundToInt() * 30
                                if (seconds != agent.shellMaxTimeoutSeconds) {
                                    updateShellMax(seconds)
                                }
                            },
                            valueRange = 60f..SHELL_MAX_MAX_SECONDS.toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
