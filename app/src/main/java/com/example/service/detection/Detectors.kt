package com.example.service.detection

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.model.ContentDetectionResult
import com.example.data.model.ContentTarget

/**
 * Scans the accessibility tree for any of the content surfaces the user chose to block.
 *
 * Platform quirks live in [com.example.data.model.ContentCatalog] as data, so supporting
 * a new app or a renamed view id is a catalogue edit rather than a code change.
 */
class ContentDetectionEngine {

    /**
     * Returns the first target found on screen, or a not-detected result.
     *
     * Order follows the caller's list, so the most specific surfaces should come first.
     */
    fun detect(root: AccessibilityNodeInfo?, targets: List<ContentTarget>): ContentDetectionResult {
        if (root == null || targets.isEmpty()) return NOT_DETECTED

        for (target in targets) {
            val matchedId = NodeScanner.findViewId(root, target.viewIdFragments)
            if (matchedId != null) {
                Log.d(TAG, target.label + " matched view id: " + matchedId)
                return ContentDetectionResult(
                    detected = true,
                    contentType = target.label,
                    contentId = target.id
                )
            }

            // Fallback for apps that draw their feeds without exposing view ids.
            val matchedTab = NodeScanner.findSelectedTab(root, target.selectedTabLabels)
            if (matchedTab != null) {
                Log.d(TAG, target.label + " matched selected tab: " + matchedTab)
                return ContentDetectionResult(
                    detected = true,
                    contentType = target.label,
                    contentId = target.id
                )
            }
        }
        return NOT_DETECTED
    }

    private companion object {
        const val TAG = "FocusGuardDetect"
        val NOT_DETECTED = ContentDetectionResult(detected = false)
    }
}
