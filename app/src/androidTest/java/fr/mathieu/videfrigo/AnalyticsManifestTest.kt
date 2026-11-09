package fr.mathieu.videfrigo

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalyticsManifestTest {
    @Suppress("DEPRECATION")
    @Test fun installedApkDoesNotRequestAdvertisingPermissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        assertFalse(permissions.contains("com.google.android.gms.permission.AD_ID"))
        assertFalse(permissions.contains("android.permission.ACCESS_ADSERVICES_AD_ID"))
        assertFalse(permissions.contains("android.permission.ACCESS_ADSERVICES_ATTRIBUTION"))
    }

    @Suppress("DEPRECATION")
    @Test fun collectionAndAutomaticScreenReportingAreOffByDefault() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val metadata = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        assertTrue(metadata.containsKey("firebase_analytics_collection_enabled"))
        assertFalse(metadata.getBoolean("firebase_analytics_collection_enabled"))
        assertTrue(metadata.containsKey("google_analytics_automatic_screen_reporting_enabled"))
        assertFalse(metadata.getBoolean("google_analytics_automatic_screen_reporting_enabled"))
    }
}
