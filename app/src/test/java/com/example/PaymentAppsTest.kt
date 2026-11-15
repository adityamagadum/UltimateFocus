package com.example

import com.example.service.PaymentApps
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentAppsTest {

    @Test
    fun recognisesListedPaymentApps() {
        listOf(
            "com.google.android.apps.nbu.paisa.user",
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp",
            "com.sbi.lotusintouch",
            "com.mobikwik_new"
        ).forEach { assertTrue(it, PaymentApps.isPaymentApp(it)) }
    }

    @Test
    fun recognisesUnlistedBanksByName() {
        listOf(
            "com.idfcfirstbank.optimus",
            "com.yesbank.iris",
            "com.upi.axispay",
            "com.bankofbaroda.bobworld"
        ).forEach { assertTrue(it, PaymentApps.isPaymentApp(it)) }
    }

    @Test
    fun leavesEverydayAppsAlone() {
        listOf(
            "com.google.android.youtube",
            "com.instagram.android",
            "com.ebay.mobile",
            "com.whatsapp",
            "com.spotify.music",
            "com.zhiliaoapp.musically",
            "com.snapchat.android",
            "com.android.chrome",
            "in.amazon.mShop.android.shopping"
        ).forEach { assertFalse(it, PaymentApps.isPaymentApp(it)) }
    }
}
