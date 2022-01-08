/*
 * Copyright (C) 2014 The Android Open Source Project
 *               2020-2022 CorvusRom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.corvus;

import android.Manifest;
import android.app.UiModeManager;
import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ThemeHelper {

    private static OverlayManager mOverlayService;

    public static int getBlendColorForPercent(int fullColor, int emptyColor, boolean reversed,
                                        int percentage) {
        float[] newColor = new float[3];
        float[] empty = new float[3];
        float[] full = new float[3];
        Color.colorToHSV(fullColor, full);
        int fullAlpha = Color.alpha(fullColor);
        Color.colorToHSV(emptyColor, empty);
        int emptyAlpha = Color.alpha(emptyColor);
        float blendFactor = percentage/100f;
        if (reversed) {
            if (empty[0] < full[0]) {
                empty[0] += 360f;
            }
            newColor[0] = empty[0] - (empty[0]-full[0])*blendFactor;
        } else {
            if (empty[0] > full[0]) {
                full[0] += 360f;
            }
            newColor[0] = empty[0] + (full[0]-empty[0])*blendFactor;
        }
        if (newColor[0] > 360f) {
            newColor[0] -= 360f;
        } else if (newColor[0] < 0) {
            newColor[0] += 360f;
        }
        newColor[1] = empty[1] + ((full[1]-empty[1])*blendFactor);
        newColor[2] = empty[2] + ((full[2]-empty[2])*blendFactor);
        int newAlpha = (int) (emptyAlpha + ((fullAlpha-emptyAlpha)*blendFactor));
        return Color.HSVToColor(newAlpha, newColor);
    }

    // Method to detect whether an overlay is enabled or not
    public static boolean isThemeEnabled(String packageName) {
        mOverlayService = new OverlayManager();
        try {
            ArrayList<OverlayInfo> infos = new ArrayList<OverlayInfo>();
            infos.addAll(mOverlayService.getOverlayInfosForTarget("android",
                    UserHandle.myUserId()));
            infos.addAll(mOverlayService.getOverlayInfosForTarget("com.android.systemui",
                    UserHandle.myUserId()));
            for (int i = 0, size = infos.size(); i < size; i++) {
                if (infos.get(i).packageName.equals(packageName)) {
                    return infos.get(i).isEnabled();
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static class OverlayManager {
        private final IOverlayManager mService;

        public OverlayManager() {
            mService = IOverlayManager.Stub.asInterface(
                    ServiceManager.getService(Context.OVERLAY_SERVICE));
        }

        public void setEnabled(String pkg, boolean enabled, int userId)
                throws RemoteException {
            mService.setEnabled(pkg, enabled, userId);
        }

        public List<OverlayInfo> getOverlayInfosForTarget(String target, int userId)
                throws RemoteException {
            return mService.getOverlayInfosForTarget(target, userId);
        }
    }

    // Method to detect whether the system dark theme is enabled or not
    public static boolean isDarkTheme(Context context) {
        UiModeManager mUiModeManager =
                context.getSystemService(UiModeManager.class);
        if (mUiModeManager == null) return false;
        int mode = mUiModeManager.getNightMode();
        return (mode == UiModeManager.MODE_NIGHT_YES);
    }

    // Switches qs tile style to user selected.
    public static void updateTileStyle(IOverlayManager om, int userId, int qsTileStyle) {
        if (qsTileStyle == 0) {
            stockTileStyle(om, userId);
        } else {
            try {
                om.setEnabled(ThemesUtils.QS_TILE_THEMES[qsTileStyle],
                        true, userId);
            } catch (RemoteException e) {
                Log.w("Can't change qs tile icon", e);
            }
        }
    }

    // Switches qs tile style back to stock.
    public static void stockTileStyle(IOverlayManager om, int userId) {
        // skip index 0
        for (int i = 0; i < ThemesUtils.QS_TILE_THEMES.length; i++) {
            String qstiletheme = ThemesUtils.QS_TILE_THEMES[i];
            try {
                om.setEnabled(qstiletheme,
                        false /*disable*/, userId);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
}
