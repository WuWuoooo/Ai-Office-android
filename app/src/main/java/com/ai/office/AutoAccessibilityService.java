package com.ai.office;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * 无障碍服务：用户在系统设置里开启后，AccessibilityController 即可代替 AI 执行屏幕操作。
 * 不监听任何事件（onAccessibilityEvent 为空），只作为操作入口。
 */
public class AutoAccessibilityService extends AccessibilityService {

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityController.setService(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不需要事件监听，留空
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        AccessibilityController.setService(null);
        return super.onUnbind(intent);
    }
}
