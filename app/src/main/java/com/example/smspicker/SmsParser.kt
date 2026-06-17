package com.example.smspicker

import java.util.regex.Pattern

object SmsParser {

    private val pickupCodePatterns = listOf(
        Pattern.compile("取件码[:：\\s]*([A-Za-z0-9\\-]+)"),
        Pattern.compile("取货码[:：\\s]*([A-Za-z0-9\\-]+)"),
        Pattern.compile("提货码[:：\\s]*([A-Za-z0-9\\-]+)"),
        Pattern.compile("凭[:：\\s]*([A-Za-z0-9\\-]{3,})"),
        Pattern.compile("编码[:：\\s]*([A-Za-z0-9\\-]+)"),
        Pattern.compile("\\b([0-9]{2,4}-[0-9]{2,5})\\b"),
        Pattern.compile("\\b([A-Za-z]?[0-9]{3,8})\\b(?!\\d)")
    )

    private val stationPatterns = listOf(
        Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9]+(驿站|代收点|收发点|便利店|菜鸟|自提点|快递点))"),
        Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9]+(菜鸟驿站|丰巢|速递易|妈妈驿站))"),
        Pattern.compile("到([\\u4e00-\\u9fa5A-Za-z0-9\\s]+?)(取|领|自提)"),
        Pattern.compile("放至([\\u4e00-\\u9fa5A-Za-z0-9\\s]+?)(店|驿站|点|处|柜)"),
        Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9\\-]+?小区?\\d*[\\u4e00-\\u9fa5]*)(店|驿站)")
    )

    private val expressSenderPatterns = listOf(
        "顺丰", "圆通", "中通", "申通", "韵达", "百世", "京东", "德邦", "邮政", "EMS",
        "极兔", "菜鸟", "天猫", "淘宝", "拼多多", "快递", "丰巢", "速递易"
    )

    fun parse(body: String, sender: String, time: Long): SmsInfo? {
        val code = extractPickupCode(body) ?: return null
        val station = extractStation(body)
        val id = SmsStorage.generateId(body, time)
        return SmsInfo(
            id = id,
            pickupCode = code,
            station = station,
            sender = sender,
            body = body,
            time = time
        )
    }

    fun isExpressSms(body: String, sender: String): Boolean {
        val containsExpressKeyword = expressSenderPatterns.any { it in body }
        val containsCodeKeyword = body.contains("取件") || body.contains("取货") ||
                body.contains("提货") || body.contains("驿站") ||
                body.contains("代收") || body.contains("自提") ||
                body.contains("凭") && body.contains("取")
        val senderIsExpress = expressSenderPatterns.any { it in sender }
        return containsExpressKeyword || containsCodeKeyword || senderIsExpress
    }

    private fun extractPickupCode(body: String): String? {
        for (pattern in pickupCodePatterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val code = matcher.group(1)?.trim()
                if (!code.isNullOrEmpty() && code.length in 2..15 && !isNumberOnlyTime(code)) {
                    return code
                }
            }
        }
        return null
    }

    private fun isNumberOnlyTime(code: String): Boolean {
        return code.matches(Regex("^[0-9]{8,}$")) && !code.contains("-")
    }

    private fun extractStation(body: String): String {
        for (pattern in stationPatterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val station = matcher.group(1)?.trim()
                if (!station.isNullOrEmpty() && station.length in 2..40) {
                    return station
                }
            }
        }
        return ""
    }
}
