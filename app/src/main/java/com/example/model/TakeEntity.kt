package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "takes")
data class TakeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val durationMs: Long,
    val targetName: String,
    val lensMm: Int,
    val timestamp: Long,
    val sampleCount: Int,
    val samplesJson: String
) {
    fun parseSamples(): List<PoseSample> {
        val list = mutableListOf<PoseSample>()
        try {
            val array = JSONArray(samplesJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val t = obj.optLong("t", 0L)
                val posArr = obj.optJSONArray("pos")
                val quatArr = obj.optJSONArray("quat")
                val pos = if (posArr != null) {
                    listOf(
                        posArr.optDouble(0, 0.0).toFloat(),
                        posArr.optDouble(1, 0.0).toFloat(),
                        posArr.optDouble(2, 0.0).toFloat()
                    )
                } else listOf(0f, 0f, 0f)

                val quat = if (quatArr != null) {
                    listOf(
                        quatArr.optDouble(0, 0.0).toFloat(),
                        quatArr.optDouble(1, 0.0).toFloat(),
                        quatArr.optDouble(2, 0.0).toFloat(),
                        quatArr.optDouble(3, 1.0).toFloat()
                    )
                } else listOf(0f, 0f, 0f, 1f)

                list.add(PoseSample(t = t, pos = pos, quat = quat))
            }
        } catch (_: Exception) { }
        return list
    }

    companion object {
        fun encodeSamples(samples: List<PoseSample>): String {
            val array = JSONArray()
            for (s in samples) {
                val obj = JSONObject()
                obj.put("t", s.t)
                val p = JSONArray()
                s.pos.forEach { p.put(it.toDouble()) }
                obj.put("pos", p)
                val q = JSONArray()
                s.quat.forEach { q.put(it.toDouble()) }
                obj.put("quat", q)
                array.put(obj)
            }
            return array.toString()
        }
    }
}

data class PoseSample(
    val t: Long,
    val pos: List<Float>,
    val quat: List<Float>
)
