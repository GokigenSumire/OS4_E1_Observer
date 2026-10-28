package personal.wallpaper.os4.e1.relay;

/** 已鉴别session的粗粒度固定状态；不能由单个原因推断Hook/手势/提交已通过。 */
final class ProducerStatusSummary {
    private ProducerStatusSummary() { }
    static String compose(boolean hostReady, String hostReason, boolean armed, String inputSummary) {
        String host = hostReady ? "主LOCK就绪" : hostReason == null || hostReason.isEmpty() ? "UNKNOWN_STATE" : hostReason;
        if (hostReady && !armed) host += "（输入连接尚未开启）";
        return host + "；输入最近状态：" + (inputSummary == null || inputSummary.isEmpty() ? "尚未读取" : inputSummary);
    }
    static String describe(int producerPresent, int reasonCode) {
        if ((producerPresent != 0 && producerPresent != 1) || reasonCode < 0 || reasonCode > 17)
            return "输入状态未知";
        if (producerPresent == 0) return "输入进程未登记（加载或鉴别尚未证实）";
        switch (reasonCode) {
            case 0: return "输入状态尚未上报";
            case 1: return "等待首个锁屏输入";
            case 2: return "输入等待主壁纸就绪";
            case 3: return "背景区域已准入（未代表手势通过）";
            case 4: return "当前不是可操作锁屏";
            case 5: return "系统认证期间暂停识别";
            case 6: return "熄屏或息屏显示期间暂停识别";
            case 7: return "面板或系统转场期间暂停识别";
            case 8: return "安全背景区域为空";
            case 9: return "触点不在安全背景区域";
            case 10: return "必需安全属性尚未确认";
            case 11: return "输入来源已变更";
            case 12: return "锁屏根视图尚未确认";
            case 13: return "动作未满足手势条件";
            case 14: return "命令已接收（未代表切图已提交）";
            case 15: return "切图或编辑命令被拒绝";
            case 16: return "已请求系统认证入口（未代表认证成功）";
            case 17: return "系统认证入口暂不可达";
            default: return "输入状态未知";
        }
    }
}
