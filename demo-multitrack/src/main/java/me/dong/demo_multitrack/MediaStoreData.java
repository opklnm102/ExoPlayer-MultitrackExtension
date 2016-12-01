package me.dong.demo_multitrack;

import android.net.Uri;

public class MediaStoreData {

    public long rowId;
    public Uri uri;
    public long dateTaken;
    public long dateModified;
    public String mimeType;
    public String displayName;

    public MediaStoreData() {
    }

    public MediaStoreData(long rowId, Uri uri, long dateTaken, long dateModified, String mimeType, String displayName) {
        this.rowId = rowId;
        this.uri = uri;
        this.dateTaken = dateTaken;
        this.dateModified = dateModified;
        this.mimeType = mimeType;
        this.displayName = displayName;
    }


}
