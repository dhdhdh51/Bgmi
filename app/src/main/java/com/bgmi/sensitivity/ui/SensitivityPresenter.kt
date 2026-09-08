package com.bgmi.sensitivity.ui

import android.content.Context
import com.bgmi.sensitivity.R
import com.bgmi.sensitivity.data.SensitivityResult

/** One line of the results list. */
sealed interface SensitivityListItem {
    data class Header(val title: String) : SensitivityListItem
    data class Row(val label: String, val value: Int) : SensitivityListItem
}

/**
 * Turns a [SensitivityResult] into display rows and clipboard text.
 *
 * Ordering follows the in-game Sensitivity settings screen so the values can be
 * copied top-to-bottom while typing them into BGMI.
 */
object SensitivityPresenter {

    fun buildItems(
        context: Context,
        result: SensitivityResult,
        includeGyroscope: Boolean,
    ): List<SensitivityListItem> = buildList {
        add(SensitivityListItem.Header(context.getString(R.string.section_camera)))
        add(SensitivityListItem.Row(context.getString(R.string.row_free_look), result.camera.freeLook))
        add(SensitivityListItem.Row(context.getString(R.string.row_tpp_no_scope), result.camera.tppNoScope))
        add(SensitivityListItem.Row(context.getString(R.string.row_fpp_no_scope), result.camera.fppNoScope))

        add(SensitivityListItem.Header(context.getString(R.string.section_scopes)))
        add(SensitivityListItem.Row(context.getString(R.string.row_red_dot_tpp), result.redDotHolo2x.tpp))
        add(SensitivityListItem.Row(context.getString(R.string.row_red_dot_fpp), result.redDotHolo2x.fpp))
        add(SensitivityListItem.Row(context.getString(R.string.row_3x), result.scope3x))
        add(SensitivityListItem.Row(context.getString(R.string.row_4x), result.scope4x))
        add(SensitivityListItem.Row(context.getString(R.string.row_6x), result.scope6x))
        add(SensitivityListItem.Row(context.getString(R.string.row_8x), result.scope8x))

        add(SensitivityListItem.Header(context.getString(R.string.section_ads)))
        add(SensitivityListItem.Row(context.getString(R.string.row_ads), result.adsSensitivity))

        if (includeGyroscope) {
            // Same rows as the in-game Gyroscope tab, in the same order.
            add(SensitivityListItem.Header(context.getString(R.string.section_gyroscope)))
            add(
                SensitivityListItem.Row(
                    context.getString(R.string.row_tpp_no_scope),
                    result.gyroscope.tppNoScope,
                ),
            )
            add(
                SensitivityListItem.Row(
                    context.getString(R.string.row_fpp_no_scope),
                    result.gyroscope.fppNoScope,
                ),
            )
            add(
                SensitivityListItem.Row(
                    context.getString(R.string.row_red_dot_holo_2x),
                    result.gyroscope.redDotHolo2x,
                ),
            )
            add(SensitivityListItem.Row(context.getString(R.string.row_3x), result.gyroscope.scope3x))
            add(SensitivityListItem.Row(context.getString(R.string.row_4x), result.gyroscope.scope4x))
            add(SensitivityListItem.Row(context.getString(R.string.row_6x), result.gyroscope.scope6x))
            add(SensitivityListItem.Row(context.getString(R.string.row_8x), result.gyroscope.scope8x))
        }
    }

    /** Plain-text version used by "Copy all". */
    fun buildCopyText(
        context: Context,
        result: SensitivityResult,
        includeGyroscope: Boolean,
    ): String = buildString {
        appendLine("BGMI sensitivity — ${result.model}")
        buildItems(context, result, includeGyroscope).forEach { item ->
            when (item) {
                is SensitivityListItem.Header -> {
                    appendLine()
                    appendLine("[${item.title}]")
                }
                is SensitivityListItem.Row -> appendLine("${item.label}: ${item.value}")
            }
        }
    }.trim()
}
