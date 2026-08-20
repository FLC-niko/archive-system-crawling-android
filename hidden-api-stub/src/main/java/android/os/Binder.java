package android.os;

import android.annotation.NonNull;
import android.annotation.Nullable;

import java.io.FileDescriptor;

public class Binder implements IBinder {

    @Override
    public boolean transact(int code, @NonNull Parcel data, Parcel reply, int flags) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public String getInterfaceDescriptor() {
        throw new RuntimeException("Stub!");
    }

    public boolean pingBinder() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean isBinderAlive() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public IInterface queryLocalInterface(@NonNull String descriptor) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void dump(@NonNull FileDescriptor fd, String[] args) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void dumpAsync(@NonNull FileDescriptor fd, String[] args) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void linkToDeath(@NonNull DeathRecipient recipient, int flags) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public boolean unlinkToDeath(@NonNull DeathRecipient recipient, int flags) {
        throw new RuntimeException("Stub!");
    }

    protected boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply,
                                 int flags) throws RemoteException {
        throw new RuntimeException("Stub!");
    }
}
