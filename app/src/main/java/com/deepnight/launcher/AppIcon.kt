package com.deepnight.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun AppIcon(app: AppInfo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(context.packageManager.getApplicationIcon(app.packageName))
            .crossfade(true)
            .build(),
        contentDescription = app.name,
        modifier = modifier
    )
}
