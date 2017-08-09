package com.mediatek.systemui.ext;

import android.content.Context;
//import android.content.ContextWrapper;
import android.graphics.drawable.Drawable;
import com.mediatek.common.PluginImpl;
//import com.android.systemui.R;

/**
 * Default implementation of Plug-in definition of Navigation bar.
 */
@PluginImpl(interfaceName = "com.mediatek.systemui.ext.INavigationBarPlugin")
public class DefaultNavigationBarPlugin implements INavigationBarPlugin {

    private Context mContext;
    /**
     * Constructs a new DefaultNavigationBarPlugin instance with Context.
     * @param context A Context object
     */
    public DefaultNavigationBarPlugin(Context context) {
        mContext = context;
    }

    @Override
    public Drawable getBackImage(Drawable drawable) {
        return drawable;
    }

    @Override
    public Drawable getBackLandImage(Drawable drawable) {
        return drawable;
    }

    @Override
    public Drawable getBackImeImage(Drawable drawable) {
        return drawable;
    }

    @Override
    public Drawable getBackImelandImage(Drawable drawable) {
        return drawable;
    }

    @Override
    public Drawable getHomeImage(Drawable drawable){
        return drawable;
    }

    @Override
    public Drawable getHomeLandImage(Drawable drawable){
        return drawable;
    }

    @Override
    public Drawable getRecentImage(Drawable drawable){
        return drawable;
    }

    @Override
    public Drawable getRecentLandImage(Drawable drawable){
        return drawable;
    }
}
