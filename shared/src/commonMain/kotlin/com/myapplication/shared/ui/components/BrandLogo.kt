package com.myapplication.shared.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.sundial_icon
import org.jetbrains.compose.resources.painterResource

@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Image(
        painter = painterResource(Res.drawable.sundial_icon),
        contentDescription = "Sundial",
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}
