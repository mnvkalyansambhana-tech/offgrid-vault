package io.github.mnvkalyansambhana.offgridvault.autofill

import android.app.assist.AssistStructure
import android.content.pm.PackageManager
import android.view.View
import android.view.autofill.AutofillId
import java.security.MessageDigest

/** One fill request, reduced to what matching and filling need. Holds no secrets. */
class ParsedRequest(
    val packageName: String,
    val webDomain: String?,
    val fields: LoginFields<AutofillId>,
) {
    val ids: Array<AutofillId> get() = listOfNotNull(fields.username, fields.password).toTypedArray()

    companion object {
        /** `null` when the screen has no login fields. */
        fun parse(structure: AssistStructure): ParsedRequest? {
            val found = mutableListOf<FieldInfo<AutofillId>>()
            fun visit(node: AssistStructure.ViewNode, inheritedDomain: String?) {
                // Each web document (including every iframe) reports its own webDomain.
                val domain = node.webDomain?.takeIf { it.isNotBlank() } ?: inheritedDomain
                if (node.visibility != View.VISIBLE) return
                val id = node.autofillId
                if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT && node.isEnabled &&
                    node.importantForAutofill != View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                ) {
                    val html = node.htmlInfo?.takeIf { it.tag.equals("input", ignoreCase = true) }
                    val attrs = html?.attributes.orEmpty().associate { it.first.lowercase() to it.second.orEmpty().lowercase() }
                    found += FieldInfo(
                        id = id,
                        autofillHints = node.autofillHints?.toList().orEmpty(),
                        inputType = node.inputType,
                        htmlType = attrs["type"],
                        htmlAutocomplete = attrs["autocomplete"],
                        htmlNameOrId = listOfNotNull(attrs["name"], attrs["id"]).joinToString(" ").ifEmpty { null },
                        idEntry = node.idEntry?.lowercase(),
                        hint = node.hint?.lowercase(),
                        webDomain = domain,
                    )
                }
                if (node.importantForAutofill == View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS) return
                for (i in 0 until node.childCount) visit(node.getChildAt(i), domain)
            }
            for (w in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(w).rootViewNode, null)
            val fields = LoginFieldClassifier.classify(found) ?: return null
            return ParsedRequest(structure.activityComponent.packageName, fields.webDomain, fields)
        }

        /** P11: what the user typed into the login fields [ids] (only those), across all screens of one login flow. */
        fun typedText(structures: List<AssistStructure>, ids: Set<AutofillId>): Map<AutofillId, String> {
            val values = HashMap<AutofillId, String>()
            fun visit(node: AssistStructure.ViewNode) {
                val id = node.autofillId
                val value = node.autofillValue
                if (id != null && id in ids && value != null && value.isText) values[id] = value.textValue.toString()
                for (i in 0 until node.childCount) visit(node.getChildAt(i))
            }
            for (s in structures) for (w in 0 until s.windowNodeCount) visit(s.getWindowNodeAt(w).rootViewNode)
            return values
        }
    }
}

/** S13: SHA-256 of the requester's **current** signing cert(s), hex. `null` if unreadable (S32). */
object SigningCerts {
    fun current(packageManager: PackageManager, packageName: String): Set<String>? = try {
        @Suppress("DEPRECATION") // PackageInfoFlags variant is API 33+
        val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signing = info.signingInfo ?: return null
        val signers = if (signing.hasMultipleSigners()) {
            signing.apkContentsSigners.toList()
        } else {
            // Rotation lineage: oldest first, current last. Only the current cert is trusted.
            listOfNotNull(signing.signingCertificateHistory.lastOrNull())
        }
        signers.map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet().takeIf { it.isNotEmpty() }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
