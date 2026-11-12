package org.rabicraft.app.model

/** How one page of a comparison turned out. */
enum class DiffKind {
    IDENTICAL,
    DIFFERENT,
    DIMENSION_MISMATCH,
    MISSING
}

/** Outcome of comparing two page thumbnails; [ratio] is the share of pixels that differ. */
data class DiffResult(
    val kind: DiffKind,
    val ratio: Float
)

/**
 * One page of the comparison report: which page it is, what the verdict is, and whether each side
 * actually had that page.
 */
data class PageComparison(
    val pageNumber: Int,
    val status: DiffKind,
    val ratio: Float,
    val hasLeft: Boolean,
    val hasRight: Boolean
)

/** Localised wording the comparison report writes above each pair of pages. */
data class ComparisonLabels(
    val identical: String = "Identical",
    val different: String = "Different",
    val differentFormat: String = "Different page size",
    val missing: String = "Missing page"
)
