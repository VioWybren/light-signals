package nodomain.greensnowapps.lightsignals.testing

import com.google.android.gms.wearable.DataMap
import java.util.Properties
import org.junit.Assert.assertEquals

/**
 * Loads independent wire-contract fixtures used by both app test suites.
 */
object ContractFixtures {
    /**
     * Holds an expected legacy Data Layer path and typed field values for contract assertions.
     *
     * @property path Data Layer path used by the request or fixture.
     * @property values Expected typed DataMap entries.
     */
    data class WireState(val path: String, val values: Map<String, Any>) {
        /**
         * Builds a DataMap from the independently defined typed fixture values.
         *
         * @return The DataMap used to exercise the production decoder.
         * @throws IllegalStateException If a fixture contains an unsupported value type.
         */
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

        /**
         * Asserts that a DataMap contains the independently defined fixture values.
         *
         * @param actual Actual DataMap to compare with the independent fixture.
         * @throws AssertionError If the field names, types or values differ from the fixture.
         * @throws IllegalStateException If an expected field has no value.
         */
        fun assertMatches(actual: DataMap) {
            assertEquals("Wire keys", values.keys, actual.keySet())
            values.forEach { (key, expected) ->
                val value = checkNotNull(actual.get<Any>(key)) { "Missing wire value: $key" }
                assertEquals("Wire type for $key", expected.javaClass, value.javaClass)
                assertEquals("Wire value for $key", expected, value)
            }
        }
    }

    /**
     * Loads an independent legacy wire-state fixture from test resources.
     *
     * @param name Fixture resource name relative to the contracts directory.
     * @return The expected path and typed wire values.
     * @throws IllegalStateException If a fixture declares an unsupported field type.
     */
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

    /**
     * Opens an independent classpath contract resource; the caller closes the returned stream.
     *
     * @param name Fixture filename relative to the contracts directory.
     * @return The open fixture input stream.
     * @throws IllegalStateException If the named contract resource is missing.
     */
    private fun resource(name: String) = checkNotNull(javaClass.getResourceAsStream("/contracts/$name")) {
        "Missing shared test fixture: $name"
    }
}
