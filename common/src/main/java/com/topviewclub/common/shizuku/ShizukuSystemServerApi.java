package com.topviewclub.common.shizuku;

import android.app.IActivityManager;
import android.content.Context;
import android.content.pm.IPackageInstaller;
import android.content.pm.IPackageManager;
import android.os.RemoteException;

import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

public final class ShizukuSystemServerApi {

    private static final Singleton<IPackageManager> PACKAGE_MANAGER = new Singleton<IPackageManager>() {
        @Override
        protected IPackageManager create() {
            return IPackageManager.Stub.asInterface(
                    new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package"))
            );
        }
    };

    private static final Singleton<IActivityManager> ACTIVITY_MANAGER = new Singleton<IActivityManager>() {
        @Override
        protected IActivityManager create() {
            return IActivityManager.Stub.asInterface(
                    new ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE))
            );
        }
    };

    public static IPackageInstaller PackageManager_getPackageInstaller() throws RemoteException {
        IPackageInstaller packageInstaller = PACKAGE_MANAGER.get().getPackageInstaller();
        return IPackageInstaller.Stub.asInterface(new ShizukuBinderWrapper(packageInstaller.asBinder()));
    }

    public static void ActivityManager_forceStopPackage(
            String packageName,
            int userId
    ) throws RemoteException {
        ACTIVITY_MANAGER.get().forceStopPackage(packageName, userId);
    }

}
