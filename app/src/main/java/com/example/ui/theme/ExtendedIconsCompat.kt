@file:Suppress("PackageDirectoryMismatch")
package androidx.compose.material.icons.filled

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Lightweight Material Icon vectors to avoid bundling the 42MB material-icons-extended DEX library
val Icons.Filled.AutoFixHigh: ImageVector
    get() = Icons.Default.Star

val Icons.Filled.AddModerator: ImageVector
    get() = Icons.Default.Lock

private var _arrowDownwardIcon: ImageVector? = null
val Icons.Filled.ArrowDownward: ImageVector
    get() = _arrowDownwardIcon ?: ImageVector.Builder(
        name = "Filled.ArrowDownward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 12f)
            lineToRelative(-1.41f, -1.41f)
            lineTo(13f, 16.17f)
            verticalLineTo(4f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(12.17f)
            lineToRelative(-5.58f, -5.59f)
            lineTo(4f, 12f)
            lineToRelative(8f, 8f)
            lineToRelative(8f, -8f)
            close()
        }
    }.build().also { _arrowDownwardIcon = it }

private var _arrowUpwardIcon: ImageVector? = null
val Icons.Filled.ArrowUpward: ImageVector
    get() = _arrowUpwardIcon ?: ImageVector.Builder(
        name = "Filled.ArrowUpward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 12f)
            lineToRelative(1.41f, 1.41f)
            lineTo(11f, 7.83f)
            verticalLineTo(20f)
            horizontalLineToRelative(2f)
            verticalLineTo(7.83f)
            lineToRelative(5.58f, 5.59f)
            lineTo(20f, 12f)
            lineToRelative(-8f, -8f)
            lineToRelative(-8f, 8f)
            close()
        }
    }.build().also { _arrowUpwardIcon = it }

val Icons.Filled.CalendarMonth: ImageVector
    get() = Icons.Default.DateRange

private var _cameraAltIcon: ImageVector? = null
val Icons.Filled.CameraAlt: ImageVector
    get() = _cameraAltIcon ?: ImageVector.Builder(
        name = "Filled.CameraAlt",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 12f)
            moveToRelative(-3.2f, 0f)
            arcToRelative(3.2f, 3.2f, 0f, true, true, 6.4f, 0f)
            arcToRelative(3.2f, 3.2f, 0f, true, true, -6.4f, 0f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(9f, 2f)
            lineTo(7.17f, 4f)
            horizontalLineTo(4f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(12f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(16f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(6f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            horizontalLineToRelative(-3.17f)
            lineTo(15f, 2f)
            horizontalLineTo(9f)
            close()
            moveTo(12f, 17f)
            curveToRelative(-2.76f, 0f, -5f, -2.24f, -5f, -5f)
            reflectiveCurveToRelative(2.24f, -5f, 5f, -5f)
            reflectiveCurveToRelative(5f, 2.24f, 5f, 5f)
            reflectiveCurveToRelative(-2.24f, 5f, -5f, 5f)
            close()
        }
    }.build().also { _cameraAltIcon = it }

val Icons.Filled.CloudDone: ImageVector
    get() = Icons.Default.CheckCircle

val Icons.Filled.CloudOff: ImageVector
    get() = Icons.Default.Warning

private var _contentCopyIcon: ImageVector? = null
val Icons.Filled.ContentCopy: ImageVector
    get() = _contentCopyIcon ?: ImageVector.Builder(
        name = "Filled.ContentCopy",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(16f, 1f)
            horizontalLineTo(4f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(14f)
            horizontalLineToRelative(2f)
            verticalLineTo(3f)
            horizontalLineToRelative(12f)
            verticalLineTo(1f)
            close()
            moveTo(19f, 5f)
            horizontalLineTo(8f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(11f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(7f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(19f, 21f)
            horizontalLineTo(8f)
            verticalLineTo(7f)
            horizontalLineToRelative(11f)
            verticalLineToRelative(14f)
            close()
        }
    }.build().also { _contentCopyIcon = it }

private var _emojiEventsIcon: ImageVector? = null
val Icons.Filled.EmojiEvents: ImageVector
    get() = _emojiEventsIcon ?: ImageVector.Builder(
        name = "Filled.EmojiEvents",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(19f, 5f)
            horizontalLineToRelative(-2f)
            verticalLineTo(3f)
            horizontalLineTo(7f)
            verticalLineToRelative(2f)
            horizontalLineTo(5f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(1f)
            curveToRelative(0f, 2.55f, 1.92f, 4.63f, 4.39f, 4.94f)
            curveToRelative(0.63f, 1.5f, 1.98f, 2.63f, 3.61f, 2.96f)
            verticalLineTo(19f)
            horizontalLineTo(7f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(10f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-4f)
            verticalLineToRelative(-3.1f)
            curveToRelative(1.63f, -0.33f, 2.98f, -1.46f, 3.61f, -2.96f)
            curveTo(19.08f, 12.63f, 21f, 10.55f, 21f, 8f)
            verticalLineTo(7f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(5f, 8f)
            verticalLineTo(7f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(3.82f)
            curveTo(5.84f, 10.4f, 5f, 9.3f, 5f, 8f)
            close()
            moveTo(19f, 8f)
            curveToRelative(0f, 1.3f, -0.84f, 2.4f, -2f, 2.82f)
            verticalLineTo(7f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(1f)
            close()
        }
    }.build().also { _emojiEventsIcon = it }

val Icons.Filled.ExpandLess: ImageVector
    get() = Icons.Default.KeyboardArrowUp

val Icons.Filled.ExpandMore: ImageVector
    get() = Icons.Default.KeyboardArrowDown

val Icons.Filled.FileUpload: ImageVector
    get() = Icons.Default.Add

val Icons.Filled.FlashOn: ImageVector
    get() = Icons.Default.Star

val Icons.Filled.GroupAdd: ImageVector
    get() = Icons.Default.Add

private var _groupsIcon: ImageVector? = null
val Icons.Filled.Groups: ImageVector
    get() = _groupsIcon ?: ImageVector.Builder(
        name = "Filled.Groups",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(16f, 11f)
            curveToRelative(1.66f, 0f, 2.99f, -1.34f, 2.99f, -3f)
            reflectiveCurveTo(17.66f, 5f, 16f, 5f)
            curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
            reflectiveCurveToRelative(1.34f, 3f, 3f, 3f)
            close()
            moveTo(8f, 11f)
            curveToRelative(1.66f, 0f, 2.99f, -1.34f, 2.99f, -3f)
            reflectiveCurveTo(9.66f, 5f, 8f, 5f)
            curveTo(6.34f, 5f, 5f, 6.34f, 5f, 8f)
            reflectiveCurveToRelative(1.34f, 3f, 3f, 3f)
            close()
            moveTo(8f, 13f)
            curveToRelative(-2.33f, 0f, -7f, 1.17f, -7f, 3.5f)
            verticalLineTo(19f)
            horizontalLineToRelative(14f)
            verticalLineToRelative(-2.5f)
            curveToRelative(0f, -2.33f, -4.67f, -3.5f, -7f, -3.5f)
            close()
            moveTo(16f, 13f)
            curveToRelative(-0.29f, 0f, -0.62f, 0.02f, -0.97f, 0.05f)
            curveToRelative(1.16f, 0.84f, 1.97f, 1.97f, 1.97f, 3.45f)
            verticalLineTo(19f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(-2.5f)
            curveToRelative(0f, -2.33f, -4.67f, -3.5f, -7f, -3.5f)
            close()
        }
    }.build().also { _groupsIcon = it }

private var _historyIcon: ImageVector? = null
val Icons.Filled.History: ImageVector
    get() = _historyIcon ?: ImageVector.Builder(
        name = "Filled.History",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(13f, 3f)
            curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
            horizontalLineTo(1f)
            lineToRelative(3.89f, 3.89f)
            lineToRelative(0.07f, 0.14f)
            lineTo(9f, 12f)
            horizontalLineTo(6f)
            curveToRelative(0f, -3.87f, 3.13f, -7f, 7f, -7f)
            reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
            reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f)
            curveToRelative(-1.93f, 0f, -3.68f, -0.79f, -4.94f, -2.06f)
            lineToRelative(-1.42f, 1.42f)
            curveTo(8.27f, 19.99f, 10.51f, 21f, 13f, 21f)
            curveToRelative(4.97f, 0f, 9f, -4.03f, 9f, -9f)
            reflectiveCurveToRelative(-4.03f, -9f, -9f, -9f)
            close()
            moveTo(12f, 8f)
            verticalLineToRelative(5f)
            lineToRelative(4.28f, 2.54f)
            lineToRelative(0.72f, -1.21f)
            lineToRelative(-3.5f, -2.08f)
            verticalLineTo(8f)
            horizontalLineTo(12f)
            close()
        }
    }.build().also { _historyIcon = it }

val Icons.Filled.Key: ImageVector
    get() = Icons.Default.Lock

val Icons.Filled.LockOpen: ImageVector
    get() = Icons.Default.Lock

val Icons.Filled.NotificationsActive: ImageVector
    get() = Icons.Default.Notifications

val Icons.Filled.Palette: ImageVector
    get() = Icons.Default.Star

val Icons.Filled.PersonAdd: ImageVector
    get() = Icons.Default.Add

val Icons.Filled.PersonOff: ImageVector
    get() = Icons.Default.Close

private var _personRemoveIcon: ImageVector? = null
val Icons.Filled.PersonRemove: ImageVector
    get() = _personRemoveIcon ?: ImageVector.Builder(
        name = "Filled.PersonRemove",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(14f, 8f)
            curveToRelative(0f, -2.21f, -1.79f, -4f, -4f, -4f)
            reflectiveCurveTo(6f, 5.79f, 6f, 8f)
            reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
            reflectiveCurveTo(14f, 10.21f, 14f, 8f)
            close()
            moveTo(17f, 10f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(-2f)
            horizontalLineTo(17f)
            close()
            moveTo(2f, 18f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(-2f)
            curveToRelative(0f, -2.66f, -5.33f, -4f, -8f, -4f)
            reflectiveCurveTo(2f, 15.34f, 2f, 18f)
            close()
        }
    }.build().also { _personRemoveIcon = it }

private var _photoLibraryIcon: ImageVector? = null
val Icons.Filled.PhotoLibrary: ImageVector
    get() = _photoLibraryIcon ?: ImageVector.Builder(
        name = "Filled.PhotoLibrary",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(22f, 16f)
            verticalLineTo(4f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            horizontalLineTo(8f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(12f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(12f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            close()
            moveTo(11f, 12f)
            lineToRelative(2.03f, 2.71f)
            lineTo(16f, 11f)
            lineToRelative(4f, 5f)
            horizontalLineTo(8f)
            lineToRelative(3f, -4f)
            close()
            moveTo(2f, 6f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            verticalLineToRelative(-2f)
            horizontalLineTo(4f)
            verticalLineTo(6f)
            horizontalLineTo(2f)
            close()
        }
    }.build().also { _photoLibraryIcon = it }

val Icons.Filled.School: ImageVector
    get() = Icons.Default.Star

val Icons.Filled.Security: ImageVector
    get() = Icons.Default.Lock

val Icons.Filled.Tune: ImageVector
    get() = Icons.Default.Settings

val Icons.Filled.ViewModule: ImageVector
    get() = Icons.Default.Info

private var _visibilityIcon: ImageVector? = null
val Icons.Filled.Visibility: ImageVector
    get() = _visibilityIcon ?: ImageVector.Builder(
        name = "Filled.Visibility",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 4.5f)
            curveTo(7f, 4.5f, 2.73f, 7.61f, 1f, 12f)
            curveToRelative(1.73f, 4.39f, 6f, 7.5f, 11f, 7.5f)
            reflectiveCurveToRelative(9.27f, -3.11f, 11f, -7.5f)
            curveToRelative(-1.73f, -4.39f, -6f, -7.5f, -11f, -7.5f)
            close()
            moveTo(12f, 17f)
            curveToRelative(-2.76f, 0f, -5f, -2.24f, -5f, -5f)
            reflectiveCurveToRelative(2.24f, -5f, 5f, -5f)
            reflectiveCurveToRelative(5f, 2.24f, 5f, 5f)
            reflectiveCurveToRelative(-2.24f, 5f, -5f, 5f)
            close()
        }
    }.build().also { _visibilityIcon = it }

private var _visibilityOffIcon: ImageVector? = null
val Icons.Filled.VisibilityOff: ImageVector
    get() = _visibilityOffIcon ?: ImageVector.Builder(
        name = "Filled.VisibilityOff",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 7f)
            curveToRelative(2.76f, 0f, 5f, 2.24f, 5f, 5f)
            curveToRelative(0f, 0.65f, -0.13f, 1.26f, -0.36f, 1.83f)
            lineToRelative(2.92f, 2.92f)
            curveToRelative(1.51f, -1.26f, 2.7f, -2.89f, 3.43f, -4.75f)
            curveToRelative(-1.73f, -4.39f, -6f, -7.5f, -11f, -7.5f)
            curveToRelative(-1.4f, 0f, -2.74f, 0.25f, -3.98f, 0.7f)
            lineToRelative(2.16f, 2.16f)
            curveTo(10.74f, 7.13f, 11.35f, 7f, 12f, 7f)
            close()
            moveTo(2f, 4.27f)
            lineToRelative(2.28f, 2.28f)
            lineToRelative(0.46f, 0.46f)
            curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1f, 12f)
            curveToRelative(1.73f, 4.39f, 6f, 7.5f, 11f, 7.5f)
            curveToRelative(1.55f, 0f, 3.03f, -0.3f, 4.38f, -0.84f)
            lineToRelative(0.42f, 0.42f)
            lineTo(19.73f, 22f)
            lineTo(21f, 20.73f)
            lineTo(3.27f, 3f)
            lineTo(2f, 4.27f)
            close()
        }
    }.build().also { _visibilityOffIcon = it }

val Icons.Filled.Comment: ImageVector
    get() = Icons.Default.Email

val Icons.Filled.EditNote: ImageVector
    get() = Icons.Default.Edit

val Icons.Filled.FactCheck: ImageVector
    get() = Icons.Default.CheckCircle

val Icons.Filled.AutoStories: ImageVector
    get() = Icons.Default.Edit

private var _micIcon: ImageVector? = null
val Icons.Filled.Mic: ImageVector
    get() = _micIcon ?: ImageVector.Builder(
        name = "Filled.Mic",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 14f)
            curveToRelative(1.66f, 0f, 2.99f, -1.34f, 2.99f, -3f)
            lineTo(15f, 5f)
            curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
            reflectiveCurveTo(9f, 3.34f, 9f, 5f)
            verticalLineToRelative(6f)
            curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
            close()
            moveTo(17.3f, 11f)
            curveToRelative(0f, 3f, -2.54f, 5.1f, -5.3f, 5.1f)
            reflectiveCurveTo(6.7f, 14f, 6.7f, 11f)
            horizontalLineTo(5f)
            curveToRelative(0f, 3.41f, 2.72f, 6.23f, 6f, 6.72f)
            verticalLineTo(21f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-3.28f)
            curveToRelative(3.28f, -0.48f, 6f, -3.3f, 6f, -6.72f)
            horizontalLineToRelative(-1.7f)
            close()
        }
    }.build().also { _micIcon = it }

private var _imageIcon: ImageVector? = null
val Icons.Filled.Image: ImageVector
    get() = _imageIcon ?: ImageVector.Builder(
        name = "Filled.Image",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(21f, 19f)
            verticalLineTo(5f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            horizontalLineTo(5f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            close()
            moveTo(8.5f, 13.5f)
            lineToRelative(2.5f, 3.01f)
            lineTo(14.5f, 12f)
            lineToRelative(4.5f, 6f)
            horizontalLineTo(5f)
            lineToRelative(3.5f, -4.5f)
            close()
        }
    }.build().also { _imageIcon = it }

private var _rotateRightIcon: ImageVector? = null
val Icons.Filled.RotateRight: ImageVector
    get() = _rotateRightIcon ?: ImageVector.Builder(
        name = "Filled.RotateRight",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(15.55f, 5.55f)
            lineTo(11f, 1f)
            verticalLineToRelative(3.07f)
            curveTo(7.06f, 4.56f, 4f, 7.92f, 4f, 12f)
            reflectiveCurveToRelative(3.05f, 7.44f, 7f, 7.93f)
            verticalLineToRelative(-2.02f)
            curveToRelative(-2.84f, -0.48f, -5f, -2.94f, -5f, -5.91f)
            reflectiveCurveToRelative(2.16f, -5.43f, 5f, -5.91f)
            verticalLineTo(10f)
            lineToRelative(4.55f, -4.45f)
            close()
            moveTo(19.93f, 11f)
            curveToRelative(-0.17f, -1.39f, -0.72f, -2.73f, -1.62f, -3.89f)
            lineToRelative(-1.42f, 1.42f)
            curveToRelative(0.54f, 0.75f, 0.88f, 1.6f, 1.02f, 2.47f)
            horizontalLineToRelative(2.02f)
            close()
            moveTo(13f, 17.9f)
            verticalLineToRelative(2.02f)
            curveToRelative(1.39f, -0.17f, 2.74f, -0.71f, 3.9f, -1.61f)
            lineToRelative(-1.44f, -1.44f)
            curveToRelative(-0.75f, 0.54f, -1.59f, 0.89f, -2.46f, 1.03f)
            close()
            moveTo(16.89f, 15.48f)
            lineToRelative(1.42f, 1.41f)
            curveToRelative(0.9f, -1.16f, 1.45f, -2.5f, 1.62f, -3.89f)
            horizontalLineToRelative(-2.02f)
            curveToRelative(-0.14f, 0.87f, -0.48f, 1.72f, -1.02f, 2.48f)
            close()
        }
    }.build().also { _rotateRightIcon = it }

private var _cropIcon: ImageVector? = null
val Icons.Filled.Crop: ImageVector
    get() = _cropIcon ?: ImageVector.Builder(
        name = "Filled.Crop",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(17f, 15f)
            horizontalLineToRelative(2f)
            verticalLineTo(7f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            horizontalLineTo(9f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(8f)
            verticalLineToRelative(8f)
            close()
            moveTo(7f, 17f)
            verticalLineTo(1f)
            horizontalLineTo(5f)
            verticalLineToRelative(4f)
            horizontalLineTo(1f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(10f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(10f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-4f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(-2f)
            horizontalLineTo(7f)
            close()
        }
    }.build().also { _cropIcon = it }

private var _skipNextIcon: ImageVector? = null
val Icons.Filled.SkipNext: ImageVector
    get() = _skipNextIcon ?: ImageVector.Builder(
        name = "Filled.SkipNext",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 18f)
            lineToRelative(8.5f, -6f)
            lineTo(6f, 6f)
            verticalLineToRelative(12f)
            close()
            moveTo(16f, 6f)
            verticalLineToRelative(12f)
            horizontalLineToRelative(2f)
            verticalLineTo(6f)
            horizontalLineToRelative(-2f)
            close()
        }
    }.build().also { _skipNextIcon = it }

private var _doneAllIcon: ImageVector? = null
val Icons.Filled.DoneAll: ImageVector
    get() = _doneAllIcon ?: ImageVector.Builder(
        name = "Filled.DoneAll",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(18f, 7f)
            lineToRelative(-1.41f, -1.41f)
            lineToRelative(-6.34f, 6.34f)
            lineToRelative(1.41f, 1.41f)
            lineTo(18f, 7f)
            close()
            moveTo(22.24f, 5.59f)
            lineTo(11.66f, 16.17f)
            lineTo(7.48f, 12f)
            lineToRelative(-1.41f, 1.41f)
            lineTo(11.66f, 19f)
            lineToRelative(12f, -12f)
            lineToRelative(-1.42f, -1.41f)
            close()
            moveTo(0.41f, 13.41f)
            lineTo(6f, 19f)
            lineToRelative(1.41f, -1.41f)
            lineTo(1.83f, 12f)
            lineTo(0.41f, 13.41f)
            close()
        }
    }.build().also { _doneAllIcon = it }

private var _chatBubbleIcon: ImageVector? = null
val Icons.Filled.ChatBubble: ImageVector
    get() = _chatBubbleIcon ?: ImageVector.Builder(
        name = "Filled.ChatBubble",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 2f)
            horizontalLineTo(4f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(18f)
            lineToRelative(4f, -4f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(4f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(8f, 11f)
            horizontalLineTo(6f)
            verticalLineTo(9f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            close()
            moveTo(13f, 11f)
            horizontalLineToRelative(-2f)
            verticalLineTo(9f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            close()
            moveTo(18f, 11f)
            horizontalLineToRelative(-2f)
            verticalLineTo(9f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            close()
        }
    }.build().also { _chatBubbleIcon = it }

private var _chatBubbleOutlineIcon: ImageVector? = null
val Icons.Filled.ChatBubbleOutline: ImageVector
    get() = _chatBubbleOutlineIcon ?: ImageVector.Builder(
        name = "Filled.ChatBubbleOutline",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 2f)
            horizontalLineTo(4f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(18f)
            lineToRelative(4f, -4f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(4f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(20f, 16f)
            horizontalLineTo(6f)
            lineToRelative(-2f, 2f)
            verticalLineTo(4f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(12f)
            close()
        }
    }.build().also { _chatBubbleOutlineIcon = it }

private var _groupsOutlineIcon: ImageVector? = null
val Icons.Filled.GroupsOutline: ImageVector
    get() = _groupsOutlineIcon ?: ImageVector.Builder(
        name = "Filled.GroupsOutline",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 6f)
            curveToRelative(1.1f, 0f, 2f, 0.9f, 2f, 2f)
            reflectiveCurveToRelative(-0.9f, 2f, -2f, 2f)
            reflectiveCurveToRelative(-2f, -0.9f, -2f, -2f)
            reflectiveCurveToRelative(0.9f, -2f, 2f, -2f)
            moveTo(12f, 15f)
            curveToRelative(2.7f, 0f, 5.8f, 1.29f, 6f, 2f)
            verticalLineToRelative(1f)
            horizontalLineTo(6f)
            verticalLineToRelative(-0.99f)
            curveToRelative(0.2f, -0.72f, 3.3f, -2.01f, 6f, -2.01f)
            moveTo(12f, 4f)
            curveToRelative(-2.21f, 0f, -4f, 1.79f, -4f, 4f)
            reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
            reflectiveCurveToRelative(4f, -1.79f, 4f, -4f)
            reflectiveCurveToRelative(-1.79f, -4f, -4f, -4f)
            close()
            moveTo(12f, 13f)
            curveToRelative(-2.67f, 0f, -8f, 1.34f, -8f, 4f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(-3f)
            curveToRelative(0f, -2.66f, -5.33f, -4f, -8f, -4f)
            close()
        }
    }.build().also { _groupsOutlineIcon = it }

private var _adminPanelSettingsIcon: ImageVector? = null
val Icons.Filled.AdminPanelSettings: ImageVector
    get() = _adminPanelSettingsIcon ?: ImageVector.Builder(
        name = "Filled.AdminPanelSettings",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 1f)
            lineTo(3f, 5f)
            verticalLineToRelative(6f)
            curveToRelative(0f, 5.55f, 3.84f, 10.74f, 9f, 12f)
            curveToRelative(5.16f, -1.26f, 9f, -6.45f, 9f, -12f)
            verticalLineTo(5f)
            lineToRelative(-9f, -4f)
            close()
            moveTo(12f, 6.5f)
            lineToRelative(1.45f, 2.95f)
            lineToRelative(3.25f, 0.47f)
            lineToRelative(-2.35f, 2.29f)
            lineToRelative(0.55f, 3.24f)
            lineTo(12f, 13.92f)
            lineToRelative(-2.9f, 1.53f)
            lineToRelative(0.55f, -3.24f)
            lineTo(7.3f, 9.92f)
            lineToRelative(3.25f, -0.47f)
            lineTo(12f, 6.5f)
            close()
        }
    }.build().also { _adminPanelSettingsIcon = it }



