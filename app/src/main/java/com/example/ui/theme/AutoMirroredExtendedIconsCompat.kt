@file:Suppress("PackageDirectoryMismatch")
package androidx.compose.material.icons.automirrored.filled

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private var _compareArrowsIcon: ImageVector? = null
val Icons.AutoMirrored.Filled.CompareArrows: ImageVector
    get() = _compareArrowsIcon ?: ImageVector.Builder(
        name = "AutoMirrored.Filled.CompareArrows",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = true
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(9.01f, 14f)
            horizontalLineTo(2f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(7.01f)
            verticalLineToRelative(3f)
            lineTo(13f, 15f)
            lineToRelative(-3.99f, -4f)
            verticalLineToRelative(3f)
            close()
            moveTo(14.99f, 13f)
            verticalLineToRelative(-3f)
            horizontalLineTo(22f)
            verticalLineTo(8f)
            horizontalLineToRelative(-7.01f)
            verticalLineTo(5f)
            lineTo(11f, 9f)
            lineToRelative(3.99f, 4f)
            close()
        }
    }.build().also { _compareArrowsIcon = it }

val Icons.AutoMirrored.Filled.Logout: ImageVector
    get() = Icons.AutoMirrored.Filled.ExitToApp

private var _undoIcon: ImageVector? = null
val Icons.AutoMirrored.Filled.Undo: ImageVector
    get() = _undoIcon ?: ImageVector.Builder(
        name = "AutoMirrored.Filled.Undo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = true
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12.5f, 8f)
            curveToRelative(-2.65f, 0f, -5.05f, 0.99f, -6.9f, 2.6f)
            lineTo(2f, 7f)
            verticalLineToRelative(9f)
            horizontalLineToRelative(9f)
            lineToRelative(-3.62f, -3.62f)
            curveToRelative(1.39f, -1.16f, 3.16f, -1.88f, 5.12f, -1.88f)
            curveToRelative(3.54f, 0f, 6.55f, 2.31f, 7.6f, 5.5f)
            lineToRelative(2.37f, -0.78f)
            curveTo(21.08f, 11.03f, 17.15f, 8f, 12.5f, 8f)
            close()
        }
    }.build().also { _undoIcon = it }
