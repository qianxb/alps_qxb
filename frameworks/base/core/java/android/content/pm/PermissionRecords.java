package android.content.pm;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * @hide
 */
public class PermissionRecords implements Parcelable {
    public String pkgName;
    public String permName;
    public List<Long> requestTimes = new ArrayList<>();

    public PermissionRecords() {
    }

    public PermissionRecords(String pkgName, String permName,
            List<Long> requestTimes) {
        this.pkgName = pkgName;
        this.permName = permName;
        this.requestTimes = requestTimes;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < requestTimes.size(); i++) {
            builder.append(requestTimes.get(i) + ",");
        }
        String times = builder.toString();
        if (requestTimes.size() > 0) {
            times = times.substring(0, times.length() - 2);
        }
        return "PermissionRecords{"
            + pkgName + " " + permName + " times(" + times + ")}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int parcelableFlags) {
        dest.writeString(pkgName);
        dest.writeString(permName);
        dest.writeList(requestTimes);
    }

    public static final ClassLoaderCreator<PermissionRecords> CREATOR =
            new ClassLoaderCreator<PermissionRecords>() {
        @Override
        public PermissionRecords createFromParcel(Parcel in) {
            return createFromParcel(in, null);
        }

        @Override
        public PermissionRecords createFromParcel(Parcel in, ClassLoader loader) {
            final PermissionRecords reocrds = new PermissionRecords();
            reocrds.pkgName = in.readString();
            reocrds.permName = in.readString();
            in.readList(reocrds.requestTimes, loader);
            return reocrds;
        }

        @Override
        public PermissionRecords[] newArray(int size) {
            return new PermissionRecords[size];
        }
    };
}
