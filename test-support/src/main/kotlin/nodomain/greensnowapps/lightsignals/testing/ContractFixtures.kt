package nodomain.greensnowapps.lightsignals.testing

import com.google.android.gms.wearable.DataMap
import java.util.Properties
import org.junit.Assert.assertEquals

object ContractFixtures {
    data class WireState(val path: String, val values: Map<String, Any>) {
        fun toDataMap() = DataMap().apply {
            values.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Int -> putInt(key, value)
                    else -> error("Unsupported fixture value: $key")
                }
            }
        }

        fun assertMatches(actual: DataMap) {
            assertEquals("Wire keys", values.keys, actual.keySet())
            values.forEach { (key, expected) ->
                val value = checkNotNull(actual.get<Any>(key)) { "Missing wire value: $key" }
                assertEquals("Wire type for $key", expected.javaClass, value.javaClass)
                assertEquals("Wire value for $key", expected, value)
            }
        }
    }

    fun wireState(name: String): WireState {
        val properties = Properties().apply { resource(name).use { load(it) } }
        val values = properties.stringPropertyNames().filter { it != "path" }.associate { entry ->
            val type = entry.substringBefore('.')
            val key = entry.substringAfter('.')
            val text = properties.getProperty(entry)
            key to when (type) {
                "boolean" -> text.toBooleanStrict()
                "long" -> text.toLong()
                "float" -> text.toFloat()
                "int" -> text.toInt()
                else -> error("Unknown fixture type: $type")
            }
        }
        return WireState(properties.getProperty("path"), values)
    }

    fun sosEdges(): List<Pair<Long, Boolean>> = resource("sos-v1.csv").bufferedReader().useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith('#') }.map { line ->
            val (millis, enabled) = line.split(',')
            millis.toLong() to enabled.toBooleanStrict()
        }.toList()
    }

    private fun resource(name: String) = checkNotNull(javaClass.getResourceAsStream("/contracts/$name")) {
        "Missing shared test fixture: $name"
    }
}
