package org.rabicraft.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.rabicraft.app.data.AppLanguage
import org.rabicraft.app.ui.i18n.LocalStrings

@Composable
fun LanguageDialog(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val options = listOf(
        AppLanguage.SYSTEM to strings.systemDefault,
        AppLanguage.ENGLISH to strings.english,
        AppLanguage.FRENCH to strings.french
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.languageTitle) },
        text = {
            Column {
                for ((language, label) in options) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(language) }
                            .padding(vertical = 6.dp)
                            .testTag("language_${language.storageValue}")
                    ) {
                        RadioButton(
                            selected = current == language,
                            onClick = { onSelect(language) }
                        )
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.done)
            }
        }
    )
}
