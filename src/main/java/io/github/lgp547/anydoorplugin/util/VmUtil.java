package io.github.lgp547.anydoorplugin.util;

import com.intellij.openapi.diagnostic.Logger;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class VmUtil {

    private static final Logger LOG = Logger.getInstance(VmUtil.class);

    /**
     * -XX:+DisableAttachMechanism
     */
    public static void attachAsync(final String targetPid, final String jarFilePath, final String param, final BiConsumer<String, Exception> errHandle) {
        CompletableFuture.runAsync(() -> {
            VirtualMachineDescriptor virtualMachineDescriptor = null;
            for (VirtualMachineDescriptor descriptor : VirtualMachine.list()) {
                String pid = descriptor.id();
                if (pid.equals(targetPid)) {
                    virtualMachineDescriptor = descriptor;
                    break;
                }
            }
            VirtualMachine virtualMachine = null;
            try {
                if (null == virtualMachineDescriptor) { // 使用 attach(String pid) 这种方式
                    virtualMachine = VirtualMachine.attach(targetPid);
                } else {
                    virtualMachine = VirtualMachine.attach(virtualMachineDescriptor);
                }

                Properties targetSystemProperties = virtualMachine.getSystemProperties();
                String targetJavaVersion = JavaVersionUtils.javaVersionStr(targetSystemProperties);
                String currentJavaVersion = JavaVersionUtils.javaVersionStr();
                if (targetJavaVersion != null && currentJavaVersion != null) {
                    if (!targetJavaVersion.equals(currentJavaVersion)) {
                        LOG.warn(String.format("Current VM java version: %s do not match target VM java version: %s, attach may fail.",
                                currentJavaVersion, targetJavaVersion));
                        LOG.warn(String.format("Target VM JAVA_HOME is %s, any-door-plugin JAVA_HOME is %s, try to set the same JAVA_HOME.",
                                targetSystemProperties.getProperty("java.home"), System.getProperty("java.home")));
                    }
                }

                try {
                    virtualMachine.loadAgent(jarFilePath, param);
                } catch (IOException e) {
                    if (e.getMessage() != null && e.getMessage().contains("Non-numeric value found")) {
                        LOG.warn(e);
                        LOG.warn("It seems to use the lower version of JDK to attach the higher version of JDK.");
                        LOG.warn(
                                "This error message can be ignored, the attach may have been successful, and it will still try to connect.");
                    } else {
                        throw e;
                    }
                } catch (com.sun.tools.attach.AgentLoadException ex) {
                    if ("0".equals(ex.getMessage())) {
                        // https://stackoverflow.com/a/54454418
                        LOG.warn(ex);
                        LOG.warn("It seems to use the higher version of JDK to attach the lower version of JDK.");
                        LOG.warn(
                                "This error message can be ignored, the attach may have been successful, and it will still try to connect.");
                    } else {
                        throw ex;
                    }
                }
            } catch (Exception e) {
                LOG.warn("Failed to attach the target VM", e);
                errHandle.accept("call " + targetPid + " error [ %s ]", e);

            } finally {
                if (null != virtualMachine) {
                    try {
                        virtualMachine.detach();
                    } catch (IOException e) {
                        LOG.warn("Failed to detach the target VM", e);
                        errHandle.accept("detach from" + targetPid + " error [ %s ]", e);
                    }
                }
            }


        });
    }
}
