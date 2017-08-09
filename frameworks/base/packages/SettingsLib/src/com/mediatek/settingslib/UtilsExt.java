package com.mediatek.settingslib;

import android.content.Context;

import com.mediatek.common.MPlugin;
import com.mediatek.settingslib.ext.DefaultDrawerExt;
import com.mediatek.settingslib.ext.IDrawerExt;

public class UtilsExt {

    private static final String TAG = UtilsExt.class.getSimpleName();

    public static IDrawerExt getDrawerPlugin(Context context) {
        IDrawerExt ext;
        ext = (IDrawerExt) MPlugin.createInstance(
                    IDrawerExt.class.getName(), context);
        if (ext == null) {
            ext = new DefaultDrawerExt(context);
        }
        return ext;
    }

}
