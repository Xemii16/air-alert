package ua.alert.wear.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight inline Material Rounded vector icons for Wear OS.
 * Replaces the full material-icons-extended artifact to eliminate thousands
 * of unused icon classes and speed up watch cold start and frame rendering.
 */
object WearIcons {

    val AlertActive: ImageVector by lazy {
        ImageVector.Builder(
            name = "AlertActive",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 22f)
                curveTo(13.1f, 22f, 14f, 21.1f, 14f, 20f)
                horizontalLineTo(10f)
                curveTo(10f, 21.1f, 10.9f, 22f, 12f, 22f)
                close()
                moveTo(18f, 16f)
                verticalLineTo(11f)
                curveTo(18f, 7.93f, 16.37f, 5.36f, 13.5f, 4.68f)
                verticalLineTo(4f)
                curveTo(13.5f, 3.17f, 12.83f, 2.5f, 12f, 2.5f)
                curveTo(11.17f, 2.5f, 10.5f, 3.17f, 10.5f, 4f)
                verticalLineTo(4.68f)
                curveTo(7.64f, 5.36f, 6f, 7.92f, 6f, 11f)
                verticalLineTo(16f)
                lineTo(4.71f, 17.29f)
                curveTo(4.08f, 17.92f, 4.52f, 19f, 5.41f, 19f)
                horizontalLineTo(18.58f)
                curveTo(19.48f, 19f, 19.92f, 17.92f, 19.29f, 17.29f)
                lineTo(18f, 16f)
                close()
            }
        }.build()
    }

    val Warning: ImageVector by lazy {
        ImageVector.Builder(
            name = "Warning",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4.47f, 21f)
                horizontalLineTo(19.53f)
                curveTo(21.07f, 21f, 22.03f, 19.33f, 21.26f, 18f)
                lineTo(13.73f, 4.99f)
                curveTo(12.96f, 3.66f, 11.04f, 3.66f, 10.27f, 4.99f)
                lineTo(2.74f, 18f)
                curveTo(1.97f, 19.33f, 2.93f, 21f, 4.47f, 21f)
                close()
                moveTo(12f, 14f)
                curveTo(11.45f, 14f, 11f, 13.55f, 11f, 13f)
                verticalLineTo(11f)
                curveTo(11f, 10.45f, 11.45f, 10f, 12f, 10f)
                curveTo(12.55f, 10f, 13f, 10.45f, 13f, 11f)
                verticalLineTo(13f)
                curveTo(13f, 13.55f, 12.55f, 14f, 12f, 14f)
                close()
                moveTo(13f, 18f)
                horizontalLineTo(11f)
                verticalLineTo(16f)
                horizontalLineTo(13f)
                verticalLineTo(18f)
                close()
            }
        }.build()
    }

    val ShieldCheck: ImageVector by lazy {
        ImageVector.Builder(
            name = "ShieldCheck",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                lineTo(4f, 5.45f)
                verticalLineTo(11.09f)
                curveTo(4f, 16.14f, 7.41f, 20.85f, 12f, 22f)
                curveTo(16.59f, 20.85f, 20f, 16.14f, 20f, 11.09f)
                verticalLineTo(5.45f)
                lineTo(12f, 2f)
                close()
                moveTo(10.5f, 15.5f)
                lineTo(7f, 12f)
                lineTo(8.41f, 10.59f)
                lineTo(10.5f, 12.67f)
                lineTo(15.59f, 7.59f)
                lineTo(17f, 9f)
                lineTo(10.5f, 15.5f)
                close()
            }
        }.build()
    }

    val DismissClose: ImageVector by lazy {
        ImageVector.Builder(
            name = "DismissClose",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(18.3f, 5.71f)
                curveTo(17.91f, 5.32f, 17.28f, 5.32f, 16.89f, 5.71f)
                lineTo(12f, 10.59f)
                lineTo(7.11f, 5.7f)
                curveTo(6.72f, 5.31f, 6.09f, 5.31f, 5.7f, 5.7f)
                curveTo(5.31f, 6.09f, 5.31f, 6.72f, 5.7f, 7.11f)
                lineTo(10.59f, 12f)
                lineTo(5.7f, 16.89f)
                curveTo(5.31f, 17.28f, 5.31f, 17.91f, 5.7f, 18.3f)
                curveTo(6.09f, 18.69f, 6.72f, 18.69f, 7.11f, 18.3f)
                lineTo(12f, 13.41f)
                lineTo(16.89f, 18.3f)
                curveTo(17.28f, 18.69f, 17.91f, 18.69f, 18.3f, 18.3f)
                curveTo(18.69f, 17.91f, 18.69f, 17.28f, 18.3f, 16.89f)
                lineTo(13.41f, 12f)
                lineTo(18.3f, 7.11f)
                curveTo(18.68f, 6.73f, 18.68f, 6.09f, 18.3f, 5.71f)
                close()
            }
        }.build()
    }

    val PhoneSetup: ImageVector by lazy {
        ImageVector.Builder(
            name = "PhoneSetup",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(17f, 1.01f)
                lineTo(7f, 1f)
                curveTo(5.9f, 1f, 5f, 1.9f, 5f, 3f)
                verticalLineTo(21f)
                curveTo(5f, 22.1f, 5.9f, 23f, 7f, 23f)
                horizontalLineTo(17f)
                curveTo(18.1f, 23f, 19f, 22.1f, 19f, 21f)
                verticalLineTo(3f)
                curveTo(19f, 1.9f, 18.1f, 1.01f, 17f, 1.01f)
                close()
                moveTo(17f, 19f)
                horizontalLineTo(7f)
                verticalLineTo(5f)
                horizontalLineTo(17f)
                verticalLineTo(19f)
                close()
            }
        }.build()
    }
}
