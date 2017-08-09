package com.mediatek.nfcgsma_extras;

import android.os.Parcel;
import android.os.Parcelable;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.content.ComponentName;

public final class GSMAOffHostAppInfo implements Parcelable {
    String mLabel;
    String mDescription;
    ComponentName mComponentName;
    Drawable mBanner;

    public GSMAOffHostAppInfo() {
    }

    public GSMAOffHostAppInfo(String label, String description, ComponentName componentName,
                Drawable banner) {
        mLabel = label;
        mDescription = description;
        mComponentName = componentName;
        mBanner = banner;
    }

    /** @hide */
    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mLabel);
        out.writeString(mDescription);
        out.writeString(mComponentName.getPackageName());
        out.writeString(mComponentName.getClassName());
        Bitmap bitmap = (Bitmap) ((BitmapDrawable) mBanner).getBitmap();
        out.writeParcelable(bitmap, flags);
    }

    public String getLabel() {
        return mLabel;
    }

    public String getDescription() {
        return mDescription;
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    public Drawable getBanner() {
        return mBanner;
    }

    /** @hide */
    @Override
        public int describeContents() {

            return 0;
        }

    /** @hide */
    public static final Parcelable.Creator<GSMAOffHostAppInfo> CREATOR = new
        Parcelable.Creator<GSMAOffHostAppInfo>() {
            @Override
                public GSMAOffHostAppInfo createFromParcel(Parcel in) {
                    String label = in.readString();
                    String description = in.readString();
                    String packageName = in.readString();
                    String className = in.readString();
                    Bitmap bitmap = (Bitmap) in.readParcelable(getClass().getClassLoader());
                    return new GSMAOffHostAppInfo(label, description,
                        new ComponentName(packageName, className),
                        new BitmapDrawable(bitmap));
                }

            @Override
                public GSMAOffHostAppInfo[] newArray(int size) {
                    return new GSMAOffHostAppInfo[size];
                }
        };

}
