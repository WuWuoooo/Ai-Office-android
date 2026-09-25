package com.ai.office;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

public class BaseActivity extends Activity {

    @Override
    protected void attachBaseContext(Context newBase) {
        Context c = UiUtils.applyFontScale(newBase);
        try {
            SharedPreferences p = c.getSharedPreferences("ai_office_config", Context.MODE_PRIVATE);
            String style = p.getString("theme_style", "system");
            if ("light".equals(style)) {
                setTheme(android.R.style.Theme_DeviceDefault_Light_NoActionBar);
            } else if ("dark".equals(style)) {
                setTheme(android.R.style.Theme_DeviceDefault_NoActionBar);
            }
        } catch (Throwable t) {}
        super.attachBaseContext(c);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySystemBars();
    }

    /** 让状态栏图标颜色跟随主题；子类可以覆盖，也可以额外调用 */
    protected void applySystemBars() {
        try {
            boolean night = UiUtils.isNight(this);
            if (Build.VERSION.SDK_INT >= 21) {
                getWindow().setStatusBarColor(UiUtils.color(this, R.color.md_surface));
            }
            if (Build.VERSION.SDK_INT >= 23) {
                View dv = getWindow().getDecorView();
                int flags = dv.getSystemUiVisibility();
                if (night) flags = flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                else flags = flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                dv.setSystemUiVisibility(flags);
            }
        } catch (Throwable t) {}
    }
}