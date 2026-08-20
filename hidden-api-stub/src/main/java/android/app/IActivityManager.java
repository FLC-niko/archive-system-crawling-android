package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;

public interface IActivityManager {

    void forceStopPackage(String packageName, int userId) throws RemoteException;

    abstract class Stub extends Binder implements IActivityManager {

        public static IActivityManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }

}
