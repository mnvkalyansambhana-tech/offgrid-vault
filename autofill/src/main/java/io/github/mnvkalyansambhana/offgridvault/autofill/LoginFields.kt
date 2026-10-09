package io.github.mnvkalyansambhana.offgridvault.autofill

import android.text.InputType

/** What the parser saw about one fillable text field (AssistStructure.ViewNode, flattened). */
data class FieldInfo<Id>(
    val id: Id,
    val autofillHints: List<String> = emptyList(),
    val inputType: Int = 0,
    /** HTML `<input type=…>` for web views, lowercased. */
    val htmlType: String? = null,
    /** HTML `autocomplete`, `name` and `id` attributes, lowercased. */
    val htmlAutocomplete: String? = null,
    val htmlNameOrId: String? = null,
    /** Android view id entry name and hint text, lowercased. */
    val idEntry: String? = null,
    val hint: String? = null,
    /** `webDomain` of the nearest enclosing web document (an iframe has its own), if any. */
    val webDomain: String? = null,
)

/**
 * The username/password pair to fill. At least one is set. [webDomain] is the document both
 * fields live in (never a parent page's domain for an iframe's fields).
 */
data class LoginFields<Id>(val username: Id?, val password: Id?, val webDomain: String? = null)

/**
 * Finds the login fields (M7). Explicit signals first (autofill hints, password input types,
 * HTML `type`/`autocomplete`), name heuristics second. A username is only guessed next to a
 * password field — a lone search box is never mistaken for a login.
 */
object LoginFieldClassifier {

    private val passwordWords = Regex("pass(word|wd|code)?|pwd|passwort|contrase|mot.?de.?passe|senha")
    private val usernameWords = Regex("user|login|e.?mail|account|identifier|phone|mobile")
    private val notLogin = Regex("search|query|otp|one.?time|captcha|confirm|repeat|retype")

    fun <Id> classify(fields: List<FieldInfo<Id>>): LoginFields<Id>? {
        val passwordIndex = fields.indexOfFirst(::isPassword)
        val explicitUser = fields.indexOfFirst { !isPassword(it) && isExplicitUsername(it) }
        val usernameIndex = when {
            explicitUser >= 0 -> explicitUser
            passwordIndex > 0 -> (passwordIndex - 1 downTo 0).firstOrNull { i ->
                !isPassword(fields[i]) && (looksLikeUsername(fields[i]) || isPlainText(fields[i]))
            } ?: -1
            else -> -1
        }
        if (passwordIndex < 0 && usernameIndex < 0) return null
        val user = fields.getOrNull(usernameIndex)
        val pass = fields.getOrNull(passwordIndex)
        // Fields from different documents (e.g. a cross-site iframe next to the page's own
        // field) are never filled together.
        if (user != null && pass != null && user.webDomain != pass.webDomain) return null
        return LoginFields(user?.id, pass?.id, (pass ?: user)?.webDomain)
    }

    fun <Id> isPassword(f: FieldInfo<Id>): Boolean {
        if (f.autofillHints.any { it.lowercase().let { h -> h == "password" || h.endsWith("-password") } }) return true
        if (f.htmlAutocomplete?.let { "password" in it } == true) return true
        if (f.htmlType == "password") return true
        val cls = f.inputType and InputType.TYPE_MASK_CLASS
        val variation = f.inputType and InputType.TYPE_MASK_VARIATION
        if (cls == InputType.TYPE_CLASS_TEXT && variation in PASSWORD_TEXT_VARIATIONS) return true
        if (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return true
        val names = listOfNotNull(f.idEntry, f.htmlNameOrId, f.hint).joinToString(" ")
        return f.htmlType == null && cls == InputType.TYPE_CLASS_TEXT && passwordWords.containsMatchIn(names) && !notLogin.containsMatchIn(names)
    }

    private fun <Id> isExplicitUsername(f: FieldInfo<Id>): Boolean {
        if (f.autofillHints.any { it.lowercase() in USERNAME_HINTS }) return true
        if (f.htmlAutocomplete?.let { it.contains("username") || it.contains("email") } == true) return true
        if (f.htmlType == "email") return true
        val variation = f.inputType and InputType.TYPE_MASK_VARIATION
        return (f.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
    }

    private fun <Id> looksLikeUsername(f: FieldInfo<Id>): Boolean {
        val names = listOfNotNull(f.idEntry, f.htmlNameOrId, f.hint).joinToString(" ")
        return usernameWords.containsMatchIn(names) && !notLogin.containsMatchIn(names)
    }

    private fun <Id> isPlainText(f: FieldInfo<Id>): Boolean {
        val names = listOfNotNull(f.idEntry, f.htmlNameOrId, f.hint).joinToString(" ")
        if (notLogin.containsMatchIn(names)) return false
        return f.htmlType in setOf(null, "text", "tel") &&
            (f.inputType == 0 || (f.inputType and InputType.TYPE_MASK_CLASS) in setOf(InputType.TYPE_CLASS_TEXT, InputType.TYPE_CLASS_PHONE))
    }

    private val PASSWORD_TEXT_VARIATIONS = setOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
    )
    private val USERNAME_HINTS = setOf("username", "emailaddress", "email", "phonenumber", "phone")
}
