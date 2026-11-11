package org.rabicraft.app.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import org.rabicraft.app.data.AppLanguage
import java.util.Locale

/**
 * Strings the app itself draws. Kept in one place so a language switch can be applied without an
 * activity restart: [LocalStrings] is re-provided and Compose recomposes.
 */
data class AppStrings(
    val files: String,
    val read: String,
    val organize: String,
    val annotate: String,
    val edit: String,
    val fillSign: String,
    val protect: String,
    val editPdf: String,
    val addText: String,
    val addImage: String,
    val addShape: String,
    val whiteout: String,
    val deleteSelected: String,
    val clearPage: String,
    val editHint: String,
    val language: String,
    val languageTitle: String,
    val systemDefault: String,
    val english: String,
    val french: String,
    val done: String,
    val cancel: String,
    val add: String,
    val newTextTitle: String,
    val textHint: String,
    val colorLabel: String
)

val EnglishStrings = AppStrings(
    files = "Files",
    read = "Read",
    organize = "Organize",
    annotate = "Annotate",
    edit = "Edit",
    fillSign = "Fill & Sign",
    protect = "Protect",
    editPdf = "Edit PDF",
    addText = "Text",
    addImage = "Image",
    addShape = "Shape",
    whiteout = "Whiteout",
    deleteSelected = "Delete",
    clearPage = "Clear all",
    editHint = "Tap an item to select it, drag to move it. Exporting flattens these edits into the PDF.",
    language = "Language",
    languageTitle = "Application language",
    systemDefault = "System language",
    english = "English",
    french = "French",
    done = "Done",
    cancel = "Cancel",
    add = "Add",
    newTextTitle = "Add text",
    textHint = "Your text",
    colorLabel = "Colour"
)

val FrenchStrings = AppStrings(
    files = "Fichiers",
    read = "Lire",
    organize = "Organiser",
    annotate = "Annoter",
    edit = "Modifier",
    fillSign = "Remplir et signer",
    protect = "Protéger",
    editPdf = "Modifier le PDF",
    addText = "Texte",
    addImage = "Image",
    addShape = "Forme",
    whiteout = "Correcteur",
    deleteSelected = "Supprimer",
    clearPage = "Tout effacer",
    editHint = "Touchez un élément pour le sélectionner, faites-le glisser pour le déplacer. L'export aplatit ces modifications dans le PDF.",
    language = "Langue",
    languageTitle = "Langue de l'application",
    systemDefault = "Langue du système",
    english = "Anglais",
    french = "Français",
    done = "Terminé",
    cancel = "Annuler",
    add = "Ajouter",
    newTextTitle = "Ajouter du texte",
    textHint = "Votre texte",
    colorLabel = "Couleur"
)

/** Resolves the strings to draw for a stored preference, following the device locale for [AppLanguage.SYSTEM]. */
fun stringsFor(language: AppLanguage): AppStrings = when (language) {
    AppLanguage.ENGLISH -> EnglishStrings
    AppLanguage.FRENCH -> FrenchStrings
    AppLanguage.SYSTEM -> if (Locale.getDefault().language.equals("fr", ignoreCase = true)) {
        FrenchStrings
    } else {
        EnglishStrings
    }
}

val LocalStrings = staticCompositionLocalOf { EnglishStrings }
