package com.taandava.manager.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.taandava.manager.R

@Composable
fun ManagerLogo(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    shape: Shape = RoundedCornerShape(8.dp),
    contentDescription: String = "Manager Logo"
) {
    Image(
        painter = painterResource(id = R.drawable.app_logo),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .clip(shape)
    )
}

