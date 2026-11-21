package com.nextgen.caros.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextgen.caros.ui.components.PillButton
import com.nextgen.caros.ui.theme.LocalNg
import com.nextgen.caros.ui.theme.NgType

/** First-run welcome: explains and requests each permission. Everything can be skipped. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val ng = LocalNg.current
    Row(Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(0.8f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Text("Welcome to", style = NgType.headline.copy(color = ng.textSecondary))
            Text("NextGen", style = NgType.display.copy(color = ng.textPrimary))
            Text("CAR OS", style = NgType.title.copy(color = ng.accent, letterSpacing = 8.sp))
            Spacer(Modifier.height(12.dp))
            Text(
                "Navigation with live speed limits, camera awareness, your music and all your apps – designed for the road. " +
                    "Grant the permissions you want to use; you can change them any time in Settings › Permissions.",
                style = NgType.body.copy(color = ng.textSecondary),
            )
            Spacer(Modifier.height(28.dp))
            PillButton("Continue", icon = Icons.Filled.ArrowForward, primary = true, onClick = onDone)
        }
        LazyColumn(Modifier.weight(1.2f).fillMaxHeight(), horizontalAlignment = Alignment.Start) {
            item { PermissionList() }
        }
    }
}
