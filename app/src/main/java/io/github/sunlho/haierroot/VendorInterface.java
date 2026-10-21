package io.github.sunlho.haierroot;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Resolve the optional vendor API without linking the app to vendor classes. */
final class VendorInterface {
    static final String CLASS_NAME = "com.mstar.android.tvapi.common.TvManager";
    private final Object manager;
    private final Method command;

    private VendorInterface(Object manager, Method command) {
        this.manager = manager;
        this.command = command;
    }

    static VendorInterface connect() throws Exception {
        return connect(Class.forName(CLASS_NAME));
    }

    static VendorInterface connect(Class<?> type) throws Exception {
        Method getter = type.getMethod("getInstance");
        Method command = type.getMethod("setTvosInterfaceCommand", String.class);
        if (!Modifier.isStatic(getter.getModifiers())
                || (command.getReturnType() != boolean.class && command.getReturnType() != Boolean.class)) {
            throw new IllegalStateException("Incompatible vendor API signature");
        }
        Object manager = getter.invoke(null);
        if (!type.isInstance(manager)) throw new IllegalStateException("Vendor manager is unavailable");
        return new VendorInterface(manager, command);
    }

    void execute(String value) throws Exception {
        if (!Boolean.TRUE.equals(command.invoke(manager, value))) {
            throw new IllegalStateException("Vendor interface rejected command");
        }
    }

    static boolean isRootOutput(String output) {
        return output.matches("(?s).*\\buid=0(?:\\(|\\s|$).*");
    }
}
