package com.dchernykh.trainingrecorder.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dchernykh.trainingrecorder.localization.AppLanguage
import com.dchernykh.trainingrecorder.localization.R

/**
 * The language picker, which lives on the phone because a fifteen-item list is
 * not something to scroll on a watch.
 *
 * Choosing here changes the phone immediately and travels to the watch with the
 * rest of the settings, so both say the same thing - the alternative, a watch
 * still in English after the rider switched their phone to Kazakh, reads as a
 * bug rather than as two independent settings.
 */
@Composable
fun LanguageSettings(
    current: AppLanguage,
    onLanguageChosen: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp),
        )
        LazyColumn {
            items(AppLanguage.entries, key = { it.name }) { language ->
                LanguageRow(
                    language = language,
                    selected = language == current,
                    onClick = { onLanguageChosen(language) },
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(
    language: AppLanguage,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The whole row is the target, so the radio button itself does not have
        // to be hit - it is a thumb-sized decision on a phone-sized control.
        RadioButton(selected = selected, onClick = null)
        Text(
            text = stringResource(language.labelRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
