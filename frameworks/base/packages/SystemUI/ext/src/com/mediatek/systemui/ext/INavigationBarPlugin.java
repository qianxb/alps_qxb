package com.mediatek.systemui.ext;

import android.graphics.drawable.Drawable;

/**
 * M: the interface for Plug-in definition of navigation bar.
 */
public interface INavigationBarPlugin {

    /** Navigation bar icon interfaces. @{ */

    /**
     * Returns the back button icon.
     * @param drawable The default back image.
     * @internal
     */
    public Drawable getBackImage(Drawable drawable);

    /**
     * Returns the back button icon for landscape mode.
     * @param drawable The default back image for landscape mode.
     * @internal
     */
    public Drawable getBackLandImage(Drawable drawable);

    /**
     * Returns the back button icon for IME.
     * @param drawable The default back image for IME.
     * @internal
     */
    public Drawable getBackImeImage(Drawable drawable);

    /**
     * Returns the back button icon for IME in landscape mode.
     * @param drawable The default back image for IME in landscape mode.
     * @internal
     */
    public Drawable getBackImelandImage(Drawable drawable);

    /**
     * Returns the home button icon.
     * @param drawable The default home image.
     * @internal
     */
    public Drawable getHomeImage(Drawable drawable);

    /**
     * Returns the home button icon for landscape mode.
     * @param drawable The default home image for landscape mode.
     * @internal
     */
    public Drawable getHomeLandImage(Drawable drawable);

    /**
     * Returns the recent apps button icon.
     * @param drawable The default recent apps image.
     * @internal
     */
    public Drawable getRecentImage(Drawable drawable);

    /**
     * Returns the recent apps button icon for landscape mode.
     * @param drawable The default recent apps image for landscape mode.
     * @internal
     */
    public Drawable getRecentLandImage(Drawable drawable);
}
