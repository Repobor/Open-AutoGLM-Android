package io.repobor.autoglm;

interface IShizukuService {

    void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method

    // Screenshot operations - returns raw PNG bytes (NOT base64)
    // Client will handle: PNG decode -> Bitmap -> JPEG compress -> Base64
    byte[] captureScreenshot() = 2;

    // Input operations
    boolean tap(int x, int y) = 3;

    boolean swipe(int startX, int startY, int endX, int endY, long duration) = 4;

    boolean longPress(int x, int y, long duration) = 5;

    boolean typeText(String text) = 6;

    boolean pressBack() = 7;

    boolean pressHome() = 8;

    boolean launchApp(String packageName) = 9;

    String enableAccessibilityService(String serviceComponentName) = 10;

    // Get current app information
    String getCurrentAppName() = 11;
}
