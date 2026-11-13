package ua.alert.shared

import org.json.JSONArray
import org.json.JSONObject

data class SelectedRegionConfig(
    val stateId: String = "31",
    val stateName: String = "м. Київ",
    val districtId: String? = null,
    val districtName: String? = null,
    val communityId: String? = null,
    val communityName: String? = null
) {
    val activeRegionId: String
        get() = communityId ?: districtId ?: stateId

    val activeRegionName: String
        get() = communityName ?: districtName ?: stateName

    val fullBreadcrumb: String
        get() = listOfNotNull(stateName, districtName, communityName).distinct().joinToString(" • ")

    fun allSubscribedIds(): List<String> = listOfNotNull(stateId, districtId, communityId).distinct()

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("stateId", stateId)
        obj.put("stateName", stateName)
        if (districtId != null) obj.put("districtId", districtId)
        if (districtName != null) obj.put("districtName", districtName)
        if (communityId != null) obj.put("communityId", communityId)
        if (communityName != null) obj.put("communityName", communityName)
        return obj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): SelectedRegionConfig {
            return try {
                val obj = JSONObject(jsonStr)
                SelectedRegionConfig(
                    stateId = obj.optString("stateId", "31"),
                    stateName = obj.optString("stateName", "м. Київ"),
                    districtId = obj.optString("districtId", "").takeIf { it.isNotEmpty() },
                    districtName = obj.optString("districtName", "").takeIf { it.isNotEmpty() },
                    communityId = obj.optString("communityId", "").takeIf { it.isNotEmpty() },
                    communityName = obj.optString("communityName", "").takeIf { it.isNotEmpty() }
                )
            } catch (e: Exception) {
                SelectedRegionConfig()
            }
        }
    }
}

data class RegionNode(
    val regionId: String,
    val regionName: String,
    val regionType: String, // "State", "District", "Community"
    val regionChildIds: List<RegionNode> = emptyList()
) {
    fun toJsonObject(): JSONObject {
        val obj = JSONObject()
        obj.put("regionId", regionId)
        obj.put("regionName", regionName)
        obj.put("regionType", regionType)
        val arr = JSONArray()
        for (child in regionChildIds) {
            arr.put(child.toJsonObject())
        }
        obj.put("regionChildIds", arr)
        return obj
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): RegionNode {
            val childrenArr = obj.optJSONArray("regionChildIds")
            val children = mutableListOf<RegionNode>()
            if (childrenArr != null) {
                for (i in 0 until childrenArr.length()) {
                    children.add(fromJsonObject(childrenArr.getJSONObject(i)))
                }
            }
            return RegionNode(
                regionId = obj.optString("regionId", ""),
                regionName = obj.optString("regionName", ""),
                regionType = obj.optString("regionType", "State"),
                regionChildIds = children
            )
        }

        fun parseStatesList(jsonStr: String): List<RegionNode> {
            return try {
                val trimmed = jsonStr.trim()
                val arr = if (trimmed.startsWith("{")) {
                    JSONObject(trimmed).optJSONArray("states") ?: JSONArray()
                } else {
                    JSONArray(trimmed)
                }
                val result = mutableListOf<RegionNode>()
                for (i in 0 until arr.length()) {
                    result.add(fromJsonObject(arr.getJSONObject(i)))
                }
                result.ifEmpty { defaultUkraineRegions() }
            } catch (e: Exception) {
                defaultUkraineRegions()
            }
        }

        /**
         * Official Ukraine Alarm API (v3) region IDs and hierarchy for immediate out-of-the-box usage.
         */
        fun defaultUkraineRegions(): List<RegionNode> = listOf(
            RegionNode(
                regionId = "31",
                regionName = "м. Київ",
                regionType = "State",
                regionChildIds = emptyList()
            ),
            RegionNode(
                regionId = "14",
                regionName = "Київська область",
                regionType = "State",
                regionChildIds = listOf(
                    RegionNode(
                        regionId = "64",
                        regionName = "Бучанський район",
                        regionType = "District",
                        regionChildIds = listOf(
                            RegionNode("665", "Бучанська територіальна громада", "Community"),
                            RegionNode("669", "Ірпінська територіальна громада", "Community"),
                            RegionNode("667", "Гостомельська територіальна громада", "Community"),
                            RegionNode("666", "Вишнева територіальна громада", "Community")
                        )
                    ),
                    RegionNode(
                        regionId = "62",
                        regionName = "Білоцерківський район",
                        regionType = "District",
                        regionChildIds = listOf(
                            RegionNode("640", "Білоцерківська територіальна громада", "Community"),
                            RegionNode("650", "Узинська територіальна громада", "Community")
                        )
                    ),
                    RegionNode(
                        regionId = "63",
                        regionName = "Броварський район",
                        regionType = "District",
                        regionChildIds = listOf(
                            RegionNode("656", "Броварська територіальна громада", "Community"),
                            RegionNode("655", "Березанська територіальна громада", "Community")
                        )
                    ),
                    RegionNode(
                        regionId = "65",
                        regionName = "Вишгородський район",
                        regionType = "District",
                        regionChildIds = listOf(
                            RegionNode("677", "Вишгородська територіальна громада", "Community"),
                            RegionNode("682", "Славутицька територіальна громада", "Community")
                        )
                    ),
                    RegionNode(
                        regionId = "66",
                        regionName = "Обухівський район",
                        regionType = "District",
                        regionChildIds = listOf(
                            RegionNode("688", "Обухівська територіальна громада", "Community"),
                            RegionNode("684", "Васильківська територіальна громада", "Community")
                        )
                    )
                )
            ),
            RegionNode(
                regionId = "22",
                regionName = "Харківська область",
                regionType = "State",
                regionChildIds = listOf(
                    RegionNode(
                        regionId = "125",
                        regionName = "Харківський район",
                        regionType = "District",
                        regionChildIds = listOf(
                            RegionNode("1483", "м. Харків та Харківська територіальна громада", "Community"),
                            RegionNode("1474", "Дергачівська територіальна громада", "Community"),
                            RegionNode("1477", "Люботинська територіальна громада", "Community")
                        )
                    ),
                    RegionNode(
                        regionId = "126",
                        regionName = "Чугуївський район",
                        regionType = "District",
                        regionChildIds = listOf(
                            RegionNode("1494", "Чугуївська територіальна громада", "Community"),
                            RegionNode("1488", "Зміївська територіальна громада", "Community")
                        )
                    )
                )
            ),
            RegionNode(
                regionId = "9",
                regionName = "Дніпропетровська область",
                regionType = "State",
                regionChildIds = listOf(
                    RegionNode(
                        regionId = "41",
                        regionName = "Дніпровський район",
                        regionType = "District",
                        regionChildIds = listOf(
                            RegionNode("399", "м. Дніпро та Дніпровська територіальна громада", "Community"),
                            RegionNode("409", "Підгородненська територіальна громада", "Community")
                        )
                    ),
                    RegionNode(
                        regionId = "43",
                        regionName = "Криворізький район",
                        regionType = "District",
                        regionChildIds = listOf(
                            RegionNode("427", "м. Кривий Ріг та Криворізька територіальна громада", "Community")
                        )
                    )
                )
            ),
            RegionNode(
                regionId = "27",
                regionName = "Львівська область",
                regionType = "State",
                regionChildIds = listOf(
                    RegionNode(
                        regionId = "77",
                        regionName = "Львівський район",
                        regionType = "District",
                        regionChildIds = listOf(
                            RegionNode("855", "м. Львів та Львівська територіальна громада", "Community")
                        )
                    ),
                    RegionNode(
                        regionId = "80",
                        regionName = "Стрийський район",
                        regionType = "District",
                        regionChildIds = listOf(
                            RegionNode("888", "Стрийська територіальна громада", "Community")
                        )
                    )
                )
            ),
            RegionNode(
                regionId = "16",
                regionName = "Одеська область",
                regionType = "State",
                regionChildIds = listOf(
                    RegionNode(
                        regionId = "94",
                        regionName = "Одеський район",
                        regionType = "District",
                        regionChildIds = listOf(
                            RegionNode("1068", "м. Одеса та Одеська територіальна громада", "Community"),
                            RegionNode("1073", "Чорноморська територіальна громада", "Community")
                        )
                    )
                )
            ),
            RegionNode(
                regionId = "12",
                regionName = "Запорізька область",
                regionType = "State",
                regionChildIds = listOf(
                    RegionNode(
                        regionId = "53",
                        regionName = "Запорізький район",
                        regionType = "District",
                        regionChildIds = listOf(
                            RegionNode("533", "м. Запоріжжя та Запорізька територіальна громада", "Community")
                        )
                    )
                )
            ),
            RegionNode("4", "Вінницька область", "State"),
            RegionNode("8", "Волинська область", "State"),
            RegionNode("28", "Донецька область", "State"),
            RegionNode("10", "Житомирська область", "State"),
            RegionNode("11", "Закарпатська область", "State"),
            RegionNode("13", "Івано-Франківська область", "State"),
            RegionNode("15", "Кіровоградська область", "State"),
            RegionNode("17", "Миколаївська область", "State"),
            RegionNode("19", "Полтавська область", "State"),
            RegionNode("5", "Рівненська область", "State"),
            RegionNode("20", "Сумська область", "State"),
            RegionNode("21", "Тернопільська область", "State"),
            RegionNode("23", "Херсонська область", "State"),
            RegionNode("3", "Хмельницька область", "State"),
            RegionNode("24", "Черкаська область", "State"),
            RegionNode("26", "Чернівецька область", "State"),
            RegionNode("25", "Чернігівська область", "State")
        )
    }
}
