package org.jenkinsci.test.acceptance.recorder;

import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.proxy.CaptureType;
import org.jenkinsci.test.acceptance.junit.FailureDiagnostics;
import org.jenkinsci.test.acceptance.junit.GlobalRule;
import org.jenkinsci.test.acceptance.utils.SystemEnvironmentVariables;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;

import static org.jenkinsci.test.acceptance.recorder.HarRecorder.State.*;

/**
 * The system property RECORD_BROWSER_TRAFFIC can be set to either off, failuresOnly or always to control when browser
 * traffic should be recorded when launching tests.
 * Traffic is recorded as a HAR (https://en.wikipedia.org/wiki/.har) file based on all network interactions between the
 * browser and the Jenkins instance and then add it as JUnit attachment to the test result.
 */
@GlobalRule
public class HarRecorder extends TestWatcher {
    public enum State {
        OFF("off", false, false), FAILURES_ONLY("failuresOnly", true, false), ALWAYS("always", true, true);

        private final String value;
        private final boolean saveOnSuccess;
        private final boolean saveOnFailure;

        State(String value, boolean saveOnFailure, boolean saveOnSuccess) {
            this.value = value;
            this.saveOnFailure = saveOnFailure;
            this.saveOnSuccess = saveOnSuccess;
        }

        public boolean isSaveOnSuccess() {
            return saveOnSuccess;
        }

        public boolean isSaveOnFailure() {
            return saveOnFailure;
        }

        public boolean isRecordingEnabled() {
            return saveOnFailure || saveOnSuccess;
        }

        public String getValue() {
            return value;
        }


        public static State value(String value) {
            for (State s : values()) {
                if (s.value.equals(value)) {
                    return s;
                }
            }
            return FAILURES_ONLY;
        }
    }

    static State CAPTURE_HAR = value(SystemEnvironmentVariables.getPropertyVariableOrEnvironment("RECORD_BROWSER_TRAFFIC", FAILURES_ONLY.getValue()));

    private static BrowserMobProxy proxy;

    public static BrowserMobProxy getBrowserMobProxy() {
        if (proxy == null) {
            // start the proxy
            proxy = new BrowserMobProxyServer();
            proxy.start(0);
            // enable more detailed HAR capture, if desired (see CaptureType for the complete list)
            proxy.enableHarCaptureTypes(CaptureType.REQUEST_CONTENT, CaptureType.RESPONSE_CONTENT);
        }
        return proxy;
    }

    private FailureDiagnostics diagnostics;

    @Inject
    public HarRecorder(FailureDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    public static boolean isCaptureHarEnabled() {
        return CAPTURE_HAR.isRecordingEnabled();
    }

    @Override
    protected void succeeded(Description description) {
        if (CAPTURE_HAR.isSaveOnSuccess()) {
            recordHar();
        }
    }

    @Override
    protected void failed(Throwable e, Description description) {
        if (CAPTURE_HAR.isSaveOnFailure()) {
            recordHar();
        }
    }

    private void recordHar() {
        if (proxy != null) {
            Har har = proxy.getHar();
            File file = diagnostics.touch("jenkins.har");
            try {
                har.writeTo(file);
            } catch (IOException e) {
                System.err.println("Unable to write HAR file to " + file);
                e.printStackTrace(System.err);
            }
        }
    }
}
