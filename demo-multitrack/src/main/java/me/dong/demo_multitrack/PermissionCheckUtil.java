package me.dong.demo_multitrack;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;

/**
 * Created by Dong on 2016-11-23.
 */

public class PermissionCheckUtil {

    @TargetApi(Build.VERSION_CODES.M)
    public static boolean checkPermission(@NonNull final Context context, @NonNull final String strPermission, @NonNull final int requestCode) {

        if (ContextCompat.checkSelfPermission(context, strPermission) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale((Activity) context, strPermission)) {
                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                ActivityCompat.requestPermissions((Activity) context, new String[]{strPermission}, requestCode);
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale((Activity) context, strPermission)) {
                    new AlertDialog.Builder(context)
                            .setMessage("이 프로그램이 원활하게 동작하기 위해서는 퍼미션을 허가가 꼭 필요합니다.")
                            .setTitle("권한 부여")
                            .setPositiveButton("예", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    ActivityCompat.requestPermissions((Activity) context, new String[]{strPermission}, requestCode);
                                }
                            })
                            .setNegativeButton("아니오", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .show();
                } else {  // permission not granted
                    ActivityCompat.requestPermissions((Activity) context, new String[]{strPermission}, requestCode);
                }
            }
            // permission deny
            return false;
        } else {
            // permission granted
            return true;
        }
    }
}
