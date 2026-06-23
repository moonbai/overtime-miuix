package com.overtime.miuix.data.model

enum class OvertimeType(val label: String) {
    WORKDAY("工作日加班"),
    WEEKEND("周末加班"),
    HOLIDAY("节假日加班")
}

enum class BottomBarStyle(val label: String) {
    ICON_TEXT("图标+文字"),
    ICON_ONLY("仅图标"),
    TEXT_ONLY("仅文字")
}
