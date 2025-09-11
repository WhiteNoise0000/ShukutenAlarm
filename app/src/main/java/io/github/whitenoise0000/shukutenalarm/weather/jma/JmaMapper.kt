package io.github.whitenoise0000.shukutenalarm.weather.jma

/**
 * GSI muniCd（5桁）からJMAのclass20/class10/officeを決定するマッパー。
 * - ルール: class20候補 = muniCd + "00"
 * - 上記が存在しない場合、政令市の区とみなし「市単位」に丸める（例: 14103 -> 1410000）。
 */
class JmaMapper(private val areaRepository: AreaRepository) {

    /** muniCdからclass20コードを決定する。*/
    suspend fun muniCdToClass20(muniCd: String): String? {
        // 候補: 5桁 + "00" -> 7桁
        val c20 = (muniCd.trim() + "00")
        val area = areaRepository.getAreaMaster()
        if (area.class20s.containsKey(c20)) return c20

        // 政令指定都市の区と判断して市単位へ丸める
        // 例: 14103(横浜市西区?) -> 1410000(横浜市)
        if (muniCd.length >= 3) {
            val rounded = muniCd.substring(0, 3) + "0000"
            if (area.class20s.containsKey(rounded)) return rounded
        }
        // 念のため4桁→000の丸めも試みる（保険）
        if (muniCd.length >= 4) {
            val rounded = muniCd.substring(0, 4) + "000"
            if (area.class20s.containsKey(rounded)) return rounded
        }
        return null
    }

    /** muniCdから class20 -> class10 -> office を辿って解決する。*/
    suspend fun resolveFromMuniCd(muniCd: String): Triple<String, String, String>? {
        val class20 = muniCdToClass20(muniCd) ?: return null
        val pair = areaRepository.resolveClass10AndOfficeFromClass20(class20) ?: return null
        val class10 = pair.first
        val office = pair.second
        return Triple(class20, class10, office)
    }
}

