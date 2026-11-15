package com.example.service

import android.content.Context
import android.content.pm.PackageManager
import java.util.Locale

/**
 * Recognises payment, UPI and banking apps.
 *
 * These apps refuse to work while an accessibility service that was not installed from
 * Google Play is switched on: Google's Play Integrity "app access risk" check reports
 * such a service as UNKNOWN_CONTROLLING, and Google Pay, PhonePe and most Indian banks
 * act on that verdict. FocusGuard steps aside for them (see [PaymentPause]) instead of
 * leaving the user unable to pay.
 *
 * Matching is a curated list plus a conservative name heuristic, because there are far
 * too many bank apps to list one by one and a missed payment app is a far worse outcome
 * for the user than pausing the shield for an app that did not strictly need it.
 */
object PaymentApps {

    /** Apps confirmed to be payment, UPI or banking apps. */
    val KNOWN_PACKAGES: Set<String> = setOf(
        // UPI and wallets
        "com.google.android.apps.nbu.paisa.user", // Google Pay (India)
        "com.google.android.apps.walletnfcrel", // Google Wallet
        "com.phonepe.app", // PhonePe
        "net.one97.paytm", // Paytm
        "in.org.npci.upiapp", // BHIM
        "com.dreamplug.androidapp", // CRED
        "com.mobikwik_new", // MobiKwik
        "com.freecharge.android", // Freecharge
        "com.enstage.wibmo.hdfc", // PayZapp
        "com.samsung.android.spay", // Samsung Wallet
        // Banks
        "com.sbi.lotusintouch", // YONO SBI
        "com.sbi.SBIFreedomPlus", // YONO Lite SBI
        "com.sbi.upi", // BHIM SBI Pay
        "com.csam.icici.bank.imobile", // ICICI iMobile Pay
        "com.snapwork.hdfc", // HDFC Bank
        "com.axis.mobile", // Axis Mobile
        "com.msf.kbank.mobile", // Kotak
        "com.bankofbaroda.mconnect", // Bank of Baroda
        "com.canarabank.mobility", // Canara ai1
        "com.infrasoft.uboi" // Union Bank Vyom
    )

    /** Name parts that, as a whole word, mark a payment app ("in.org.npci.upiapp"). */
    private val PAYMENT_WORDS = setOf("upi", "bhim", "wallet", "payments", "pay", "netbanking")

    /**
     * True for a payment or banking app.
     *
     * The heuristic looks at each dot- or underscore-separated part of the package name:
     * any part containing "bank", starting with "upi", ending in "pay" (gpay, spay), or
     * equal to a payment word. "com.ebay" does not match; "com.example.bankapp" does.
     */
    fun isPaymentApp(packageName: String): Boolean {
        if (packageName in KNOWN_PACKAGES) return true
        val parts = packageName.lowercase(Locale.US).split('.', '_')
        return parts.any { part ->
            part.contains("bank") ||
                part.startsWith("upi") ||
                (part.length > 3 && part.endsWith("pay")) ||
                part in PAYMENT_WORDS
        }
    }

    /** The name the user sees for [packageName], or null if it cannot be read. */
    fun labelFor(context: Context, packageName: String): String? = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: Exception) {
        null
    }
}
