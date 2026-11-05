package com.luofelix.laninbox

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal object AppIcons {
    val Back = outline("Back", "M19 12 H5 M12 19 L5 12 L12 5")
    val Close = outline("Close", "M6 6 L18 18 M6 18 L18 6")
    val More = outline("More", "M12 5 H12.1 M12 12 H12.1 M12 19 H12.1")
    val Computer = outline("Computer", "M4 4 H20 Q21 4 21 5 V16 Q21 17 20 17 H4 Q3 17 3 16 V5 Q3 4 4 4 Z M8 21 H16 M12 17 V21")
    val Scan = outline("Scan", "M4 8 V4 H8 M16 4 H20 V8 M20 16 V20 H16 M8 20 H4 V16 M8 12 H16")
    val Image = outline("Image", "M5 4 H19 Q20 4 20 5 V19 Q20 20 19 20 H5 Q4 20 4 19 V5 Q4 4 5 4 Z M4 16 L9 11 L13 15 L16 12 L20 16 M9 8 H9.1")
    val Document = outline("Document", "M5 3 H14 L19 8 V20 Q19 21 18 21 H6 Q5 21 5 20 Z M14 3 V8 H19 M9 12 H15 M9 16 H15")
    val Folder = outline("Folder", "M3 18 V6 Q3 5 4 5 H9 L11 7 H20 Q21 7 21 8 V18 Q21 19 20 19 H4 Q3 19 3 18 Z")
    val Link = outline("Link", "M10 13 L14 9 M8.5 15.5 L7 17 A3 3 0 0 1 2.8 12.8 L6.5 9.1 A3 3 0 0 1 10.7 9.1 M15.5 8.5 L17 7 A3 3 0 0 1 21.2 11.2 L17.5 14.9 A3 3 0 0 1 13.3 14.9")
    val Code = outline("Code", "M8 6 L2 12 L8 18 M16 6 L22 12 L16 18 M14 4 L10 20")
    val Send = outline("Send", "M21 3 L10 14 M21 3 L14 21 L10 14 L3 10 Z")
    val Retry = outline("Retry", "M20 4 V10 H14 M20 10 A8 8 0 1 0 19 17")

    private fun outline(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
}
