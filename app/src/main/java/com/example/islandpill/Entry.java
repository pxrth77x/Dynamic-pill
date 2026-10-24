package com.example.islandpill;

import android.content.Context;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Entry implements IXposedHookLoadPackage {
    private static boolean started;

    @Override
    public void handleLoadPackage(LoadPackageParam lpp) {
        if (!"com.android.systemui".equals(lpp.packageName)) return;
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.SystemUIApplication", lpp.classLoader, "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (started) return;
                        started = true;
                        try {
                            new IslandController((Context) param.thisObject).start();
                        } catch (Throwable t) {
                            XposedBridge.log("IslandPill start failed: " + t);
                        }
                    }
                });
        } catch (Throwable t) {
            XposedBridge.log("IslandPill hook failed: " + t);
        }
    }
}
