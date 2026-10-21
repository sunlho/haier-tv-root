package io.github.sunlho.haierroot;

import org.junit.Test;
import static org.junit.Assert.*;

public class VendorInterfaceTest {
    public static class Available {
        static String received;
        public static Available getInstance() { return new Available(); }
        public boolean setTvosInterfaceCommand(String value) { received = value; return true; }
    }
    public static class Rejected {
        public static Rejected getInstance() { return new Rejected(); }
        public boolean setTvosInterfaceCommand(String value) { return false; }
    }
    public static class MissingMethod {
        public static MissingMethod getInstance() { return new MissingMethod(); }
    }
    public static class WrongReturn {
        public static WrongReturn getInstance() { return new WrongReturn(); }
        public String setTvosInterfaceCommand(String value) { return "true"; }
    }
    public static class NullManager {
        public static NullManager getInstance() { return null; }
        public boolean setTvosInterfaceCommand(String value) { return true; }
    }
    @Test public void invokesAvailableInterface() throws Exception {
        VendorInterface.connect(Available.class).execute("CMD=21,test");
        assertEquals("CMD=21,test", Available.received);
    }
    @Test(expected = IllegalStateException.class) public void rejectsUnacceptedCommand() throws Exception {
        VendorInterface.connect(Rejected.class).execute("test");
    }
    @Test(expected = NoSuchMethodException.class) public void handlesMissingMethod() throws Exception {
        VendorInterface.connect(MissingMethod.class);
    }
    @Test(expected = IllegalStateException.class) public void rejectsDifferentApi() throws Exception {
        VendorInterface.connect(WrongReturn.class);
    }
    @Test(expected = IllegalStateException.class) public void handlesMissingService() throws Exception {
        VendorInterface.connect(NullManager.class);
    }
    @Test public void requiresActualRootIdentity() {
        assertTrue(VendorInterface.isRootOutput("uid=0(root) gid=0(root)"));
        assertTrue(VendorInterface.isRootOutput("uid=0 gid=0"));
        assertFalse(VendorInterface.isRootOutput("uid=2000(shell) gid=2000(shell)"));
        assertFalse(VendorInterface.isRootOutput("uid=10000(app)"));
        assertFalse(VendorInterface.isRootOutput("uid=00(root)"));
        assertFalse(VendorInterface.isRootOutput("interface_return=true"));
    }
}
