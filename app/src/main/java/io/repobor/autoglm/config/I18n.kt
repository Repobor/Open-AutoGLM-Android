package io.repobor.autoglm.config

/**
 * Internationalization (i18n) strings for UI elements.
 * Supports Chinese and English languages.
 */
object I18n {

    /**
     * Language codes
     */
    const val LANG_CHINESE = "zh"
    const val LANG_ENGLISH = "en"

    /**
     * Base interface for localized strings
     */
    interface LocalizedStrings {
        // App
        val APP_NAME: String
        val APP_DESCRIPTION: String

        // Navigation
        val NAV_SETTINGS: String
        val NAV_TASK: String
        val NAV_LOG: String

        // Settings Screen
        val SETTINGS_TITLE: String
        val SETTINGS_ENDPOINT: String
        val SETTINGS_ENDPOINT_HINT: String
        val SETTINGS_API_KEY: String
        val SETTINGS_API_KEY_HINT: String
        val SETTINGS_MODEL_NAME: String
        val SETTINGS_MODEL_NAME_HINT: String
        val SETTINGS_MAX_STEPS: String
        val SETTINGS_MAX_STEPS_HINT: String
        val SETTINGS_LANGUAGE: String
        val SETTINGS_LANGUAGE_CHINESE: String
        val SETTINGS_LANGUAGE_ENGLISH: String
        val SETTINGS_TEST_CONNECTION: String
        val SETTINGS_ENABLE_SERVICE: String
        val SETTINGS_SAVE: String
        val SETTINGS_SAVED: String
        val SETTINGS_CONNECTION_SUCCESS: String
        val SETTINGS_CONNECTION_FAILED: String

        // Task Screen
        val TASK_TITLE: String
        val TASK_DESCRIPTION: String
        val TASK_DESCRIPTION_HINT: String
        val TASK_START: String
        val TASK_STOP: String
        val TASK_RUNNING: String
        val TASK_COMPLETED: String
        val TASK_FAILED: String
        val TASK_STEP: String
        val TASK_CURRENT_APP: String
        val TASK_ACTION: String
        val TASK_THINKING: String

        // Log Screen
        val LOG_TITLE: String
        val LOG_EMPTY: String
        val LOG_CLEAR: String
        val LOG_EXPORT: String
        val LOG_TASK: String
        val LOG_STATUS: String
        val LOG_TIMESTAMP: String
        val LOG_DETAILS: String

        // Error Messages
        val ERROR_NETWORK: String
        val ERROR_API: String
        val ERROR_SCREENSHOT: String
        val ERROR_ACTION_FAILED: String
        val ERROR_SERVICE_NOT_ENABLED: String
        val ERROR_INVALID_INPUT: String
        val ERROR_PERMISSION_DENIED: String
        val ERROR_UNKNOWN: String

        // Status Messages
        val STATUS_IDLE: String
        val STATUS_RUNNING: String
        val STATUS_SUCCESS: String
        val STATUS_FAILED: String
        val STATUS_STOPPED: String

        // Action Names
        val ACTION_TAP: String
        val ACTION_TYPE: String
        val ACTION_SWIPE: String
        val ACTION_LAUNCH: String
        val ACTION_BACK: String
        val ACTION_HOME: String
        val ACTION_LONG_PRESS: String
        val ACTION_DOUBLE_TAP: String
        val ACTION_WAIT: String
        val ACTION_FINISH: String

        // Dialogs
        val DIALOG_CONFIRM: String
        val DIALOG_CANCEL: String
        val DIALOG_OK: String
        val DIALOG_ENABLE_SERVICE_TITLE: String
        val DIALOG_ENABLE_SERVICE_MESSAGE: String
        val DIALOG_CLEAR_LOG_TITLE: String
        val DIALOG_CLEAR_LOG_MESSAGE: String

        // Picture-in-Picture (PiP)
        val PIP_BUTTON: String
        val PIP_STEP_PROGRESS: String
        val PIP_STOP: String
        val PIP_RETURN: String
        val PIP_NO_LOGS: String

        // Floating Window
        val TASK_RETURN_TO_APP: String
    }

    /**
     * Chinese UI strings
     */
    object Chinese : LocalizedStrings {
        // App
        override val APP_NAME = "AutoGLM"
        override val APP_DESCRIPTION = "AI驱动的自动化助手"

        // Navigation
        override val NAV_SETTINGS = "设置"
        override val NAV_TASK = "任务"
        override val NAV_LOG = "日志"

        // Settings Screen
        override val SETTINGS_TITLE = "API配置"
        override val SETTINGS_ENDPOINT = "API端点"
        override val SETTINGS_ENDPOINT_HINT = "例如: http://192.168.1.100:8000"
        override val SETTINGS_API_KEY = "API密钥"
        override val SETTINGS_API_KEY_HINT = "输入您的API密钥"
        override val SETTINGS_MODEL_NAME = "模型名称"
        override val SETTINGS_MODEL_NAME_HINT = "例如: autoglm-phone-9b"
        override val SETTINGS_MAX_STEPS = "最大步数"
        override val SETTINGS_MAX_STEPS_HINT = "任务执行的最大步数"
        override val SETTINGS_LANGUAGE = "语言"
        override val SETTINGS_LANGUAGE_CHINESE = "中文"
        override val SETTINGS_LANGUAGE_ENGLISH = "English"
        override val SETTINGS_TEST_CONNECTION = "测试连接"
        override val SETTINGS_ENABLE_SERVICE = "启用无障碍服务"
        override val SETTINGS_SAVE = "保存"
        override val SETTINGS_SAVED = "设置已保存"
        override val SETTINGS_CONNECTION_SUCCESS = "连接成功"
        override val SETTINGS_CONNECTION_FAILED = "连接失败"

        // Task Screen
        override val TASK_TITLE = "任务执行"
        override val TASK_DESCRIPTION = "任务描述"
        override val TASK_DESCRIPTION_HINT = "输入您想要完成的任务..."
        override val TASK_START = "开始任务"
        override val TASK_STOP = "停止任务"
        override val TASK_RUNNING = "运行中..."
        override val TASK_COMPLETED = "任务完成"
        override val TASK_FAILED = "任务失败"
        override val TASK_STEP = "步骤"
        override val TASK_CURRENT_APP = "当前应用"
        override val TASK_ACTION = "操作"
        override val TASK_THINKING = "思考"

        // Log Screen
        override val LOG_TITLE = "执行历史"
        override val LOG_EMPTY = "暂无执行记录"
        override val LOG_CLEAR = "清除历史"
        override val LOG_EXPORT = "导出日志"
        override val LOG_TASK = "任务"
        override val LOG_STATUS = "状态"
        override val LOG_TIMESTAMP = "时间"
        override val LOG_DETAILS = "详情"

        // Error Messages
        override val ERROR_NETWORK = "网络错误"
        override val ERROR_API = "API错误"
        override val ERROR_SCREENSHOT = "截图失败"
        override val ERROR_ACTION_FAILED = "操作执行失败"
        override val ERROR_SERVICE_NOT_ENABLED = "无障碍服务未启用"
        override val ERROR_INVALID_INPUT = "输入无效"
        override val ERROR_PERMISSION_DENIED = "权限被拒绝"
        override val ERROR_UNKNOWN = "未知错误"

        // Status Messages
        override val STATUS_IDLE = "空闲"
        override val STATUS_RUNNING = "执行中"
        override val STATUS_SUCCESS = "成功"
        override val STATUS_FAILED = "失败"
        override val STATUS_STOPPED = "已停止"

        // Action Names
        override val ACTION_TAP = "点击"
        override val ACTION_TYPE = "输入"
        override val ACTION_SWIPE = "滑动"
        override val ACTION_LAUNCH = "启动应用"
        override val ACTION_BACK = "返回"
        override val ACTION_HOME = "主屏幕"
        override val ACTION_LONG_PRESS = "长按"
        override val ACTION_DOUBLE_TAP = "双击"
        override val ACTION_WAIT = "等待"
        override val ACTION_FINISH = "完成"

        // Dialogs
        override val DIALOG_CONFIRM = "确认"
        override val DIALOG_CANCEL = "取消"
        override val DIALOG_OK = "确定"
        override val DIALOG_ENABLE_SERVICE_TITLE = "启用无障碍服务"
        override val DIALOG_ENABLE_SERVICE_MESSAGE = "请在系统设置中启用AutoGLM无障碍服务"
        override val DIALOG_CLEAR_LOG_TITLE = "清除历史"
        override val DIALOG_CLEAR_LOG_MESSAGE = "确定要清除所有执行历史吗?"

        // Picture-in-Picture (PiP)
        override val PIP_BUTTON = "画中画"
        override val PIP_STEP_PROGRESS = "步骤"
        override val PIP_STOP = "停止"
        override val PIP_RETURN = "返回"
        override val PIP_NO_LOGS = "暂无日志"

        // Floating Window
        override val TASK_RETURN_TO_APP = "返回应用"
    }

    /**
     * English UI strings
     */
    object English : LocalizedStrings {
        // App
        override val APP_NAME = "AutoGLM"
        override val APP_DESCRIPTION = "AI-powered Phone Automation Assistant"

        // Navigation
        override val NAV_SETTINGS = "Settings"
        override val NAV_TASK = "Task"
        override val NAV_LOG = "Log"

        // Settings Screen
        override val SETTINGS_TITLE = "API Configuration"
        override val SETTINGS_ENDPOINT = "API Endpoint"
        override val SETTINGS_ENDPOINT_HINT = "e.g., http://192.168.1.100:8000"
        override val SETTINGS_API_KEY = "API Key"
        override val SETTINGS_API_KEY_HINT = "Enter your API key"
        override val SETTINGS_MODEL_NAME = "Model Name"
        override val SETTINGS_MODEL_NAME_HINT = "e.g., autoglm-phone-9b"
        override val SETTINGS_MAX_STEPS = "Max Steps"
        override val SETTINGS_MAX_STEPS_HINT = "Maximum steps for task execution"
        override val SETTINGS_LANGUAGE = "Language"
        override val SETTINGS_LANGUAGE_CHINESE = "中文"
        override val SETTINGS_LANGUAGE_ENGLISH = "English"
        override val SETTINGS_TEST_CONNECTION = "Test Connection"
        override val SETTINGS_ENABLE_SERVICE = "Enable Accessibility Service"
        override val SETTINGS_SAVE = "Save"
        override val SETTINGS_SAVED = "Settings saved"
        override val SETTINGS_CONNECTION_SUCCESS = "Connection successful"
        override val SETTINGS_CONNECTION_FAILED = "Connection failed"

        // Task Screen
        override val TASK_TITLE = "Task Execution"
        override val TASK_DESCRIPTION = "Task Description"
        override val TASK_DESCRIPTION_HINT = "Enter the task you want to complete..."
        override val TASK_START = "Start Task"
        override val TASK_STOP = "Stop Task"
        override val TASK_RUNNING = "Running..."
        override val TASK_COMPLETED = "Task completed"
        override val TASK_FAILED = "Task failed"
        override val TASK_STEP = "Step"
        override val TASK_CURRENT_APP = "Current App"
        override val TASK_ACTION = "Action"
        override val TASK_THINKING = "Thinking"

        // Log Screen
        override val LOG_TITLE = "Execution History"
        override val LOG_EMPTY = "No execution history"
        override val LOG_CLEAR = "Clear History"
        override val LOG_EXPORT = "Export Log"
        override val LOG_TASK = "Task"
        override val LOG_STATUS = "Status"
        override val LOG_TIMESTAMP = "Time"
        override val LOG_DETAILS = "Details"

        // Error Messages
        override val ERROR_NETWORK = "Network error"
        override val ERROR_API = "API error"
        override val ERROR_SCREENSHOT = "Screenshot failed"
        override val ERROR_ACTION_FAILED = "Action execution failed"
        override val ERROR_SERVICE_NOT_ENABLED = "Accessibility service not enabled"
        override val ERROR_INVALID_INPUT = "Invalid input"
        override val ERROR_PERMISSION_DENIED = "Permission denied"
        override val ERROR_UNKNOWN = "Unknown error"

        // Status Messages
        override val STATUS_IDLE = "Idle"
        override val STATUS_RUNNING = "Running"
        override val STATUS_SUCCESS = "Success"
        override val STATUS_FAILED = "Failed"
        override val STATUS_STOPPED = "Stopped"

        // Action Names
        override val ACTION_TAP = "Tap"
        override val ACTION_TYPE = "Type"
        override val ACTION_SWIPE = "Swipe"
        override val ACTION_LAUNCH = "Launch"
        override val ACTION_BACK = "Back"
        override val ACTION_HOME = "Home"
        override val ACTION_LONG_PRESS = "Long Press"
        override val ACTION_DOUBLE_TAP = "Double Tap"
        override val ACTION_WAIT = "Wait"
        override val ACTION_FINISH = "Finish"

        // Dialogs
        override val DIALOG_CONFIRM = "Confirm"
        override val DIALOG_CANCEL = "Cancel"
        override val DIALOG_OK = "OK"
        override val DIALOG_ENABLE_SERVICE_TITLE = "Enable Accessibility Service"
        override val DIALOG_ENABLE_SERVICE_MESSAGE = "Please enable AutoGLM accessibility service in system settings"
        override val DIALOG_CLEAR_LOG_TITLE = "Clear History"
        override val DIALOG_CLEAR_LOG_MESSAGE = "Are you sure you want to clear all execution history?"

        // Picture-in-Picture (PiP)
        override val PIP_BUTTON = "PiP"
        override val PIP_STEP_PROGRESS = "Step"
        override val PIP_STOP = "Stop"
        override val PIP_RETURN = "Return"
        override val PIP_NO_LOGS = "No logs"

        // Floating Window
        override val TASK_RETURN_TO_APP = "Return to App"
    }

    /**
     * Get strings object by language code.
     * @param language Language code ("zh" or "en")
     * @return The appropriate strings object
     */
    fun getStrings(language: String): LocalizedStrings {
        return when (language.lowercase()) {
            LANG_CHINESE, "chinese", "中文" -> Chinese
            LANG_ENGLISH, "english" -> English
            else -> English // Default to English
        }
    }

    /**
     * Helper class for dynamic string access
     */
    data class Strings(
        val appName: String,
        val appDescription: String,
        val navSettings: String,
        val navTask: String,
        val navLog: String,
        val settingsTitle: String,
        val taskTitle: String,
        val logTitle: String
    )

    /**
     * Get localized strings bundle.
     * @param language Language code
     * @return Strings bundle
     */
    fun getStringBundle(language: String): Strings {
        val s = getStrings(language)
        return Strings(
            appName = s.APP_NAME,
            appDescription = s.APP_DESCRIPTION,
            navSettings = s.NAV_SETTINGS,
            navTask = s.NAV_TASK,
            navLog = s.NAV_LOG,
            settingsTitle = s.SETTINGS_TITLE,
            taskTitle = s.TASK_TITLE,
            logTitle = s.LOG_TITLE
        )
    }
}
