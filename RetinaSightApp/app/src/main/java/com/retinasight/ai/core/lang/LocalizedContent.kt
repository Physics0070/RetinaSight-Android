package com.retinasight.ai.core.lang

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Applies the chosen language to everything drawn inside [content].
 *
 * IMPORTANT - why this wraps instead of replacing:
 *
 * The obvious implementation is to provide `LocalContext` with the result of
 * `createConfigurationContext(config)`. That works for text and then crashes
 * the moment anything asks for an activity result:
 *
 *     IllegalStateException: No ActivityResultRegistryOwner was provided
 *
 * `createConfigurationContext` returns a fresh ContextImpl, so the
 * ContextWrapper chain back to the Activity is gone. Compose finds owners
 * (ActivityResultRegistryOwner, LifecycleOwner, ...) by walking that chain, so
 * the camera permission request and the photo picker both blow up.
 *
 * Wrapping the ORIGINAL context and overriding only the resources keeps the
 * chain intact while still resolving strings in the selected language.
 */
private class LocalizedContextWrapper(
    base: Context,
    private val localizedResources: Resources
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
    override fun getAssets(): AssetManager = localizedResources.assets
}

@Composable
fun LocalizedContent(
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current

    val localizedContext = remember(language, baseContext) {
        val config = Configuration(baseContext.resources.configuration).apply {
            setLocale(language.locale)
            setLayoutDirection(language.locale)
        }
        val resources = baseContext.createConfigurationContext(config).resources
        LocalizedContextWrapper(baseContext, resources)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        content = content
    )
}
