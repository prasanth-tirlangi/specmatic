package run.qontract.core

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import run.qontract.core.pattern.*
import run.qontract.core.value.KafkaMessage
import run.qontract.core.value.StringValue
import run.qontract.core.value.True
import run.qontract.core.value.Value
import java.util.*
import kotlin.collections.HashMap

internal class ScenarioTest {
    @Test
    fun `should generate one test scenario when there are no examples`() {
        val scenario = Scenario("test", HttpRequestPattern(), HttpResponsePattern(), HashMap(), LinkedList(), HashMap(), HashMap(), KafkaMessagePattern())
        scenario.generateTestScenarios().let {
            assertThat(it.size).isEqualTo(1)
        }
    }

    @Test
    fun `should generate two test scenarios when there are two rows in examples`() {
        val patterns = Examples()
        patterns.rows.add(0, Row())
        patterns.rows.add(1, Row())
        val scenario = Scenario("test", HttpRequestPattern(), HttpResponsePattern(), HashMap(), listOf(patterns), HashMap(), HashMap(), KafkaMessagePattern())
        scenario.generateTestScenarios().let {
            assertThat(it.size).isEqualTo(2)
        }
    }

    @Test
    fun `should not match when there is an Exception`() {
        val httpResponsePattern = mockk<HttpResponsePattern>(relaxed = true)
        every { httpResponsePattern.matches(any(), any()) }.throws(ContractException("message"))
        val scenario = Scenario("test", HttpRequestPattern(), httpResponsePattern, HashMap(), LinkedList(), HashMap(), HashMap(), KafkaMessagePattern())
        scenario.matches(HttpResponse.EMPTY).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).report()).isEqualTo(FailureReport(listOf(), listOf("Exception: message")))
        }
    }

    @Test
    fun `given a pattern in an example, facts declare without a value should pick up the pattern`() {
        val row = Row(listOf("id"), listOf("(string)"))

        val newState = newExpectedServerStateBasedOn(row, mapOf("id" to True), HashMap(), Resolver())

        assertThat(newState.getValue("id").toStringValue()).isNotEqualTo("(string)")
        assertThat(newState.getValue("id").toStringValue().trim().length).isGreaterThan(0)
    }

    @Test
    fun `given a pattern in an example in a scenario generated based on a row, facts declare without a value should pick up the pattern`() {
        val row = Row(listOf("id"), listOf("(string)"))
        val example = Examples(mutableListOf("id"))
        example.addRows(listOf(row))

        val state = HashMap(mapOf<String, Value>("id" to True))
        val scenario = Scenario("Test", HttpRequestPattern(urlMatcher = URLMatcher(emptyMap(), emptyList(), path="/")), HttpResponsePattern(status=200), state, listOf(example), HashMap(), HashMap(), KafkaMessagePattern())

        val testScenarios = scenario.generateTestScenarios()
        val newState = testScenarios.first().expectedFacts

        assertThat(newState.getValue("id").toStringValue()).isNotEqualTo("(string)")
        assertThat(newState.getValue("id").toStringValue().trim().length).isGreaterThan(0)
    }

    @Test
    fun `scenario will match a kafka mock message`() {
        val row = Row(listOf("id"), listOf("(string)"))
        val example = Examples(mutableListOf("id"))
        example.addRows(listOf(row))

        val kafkaMessagePattern = KafkaMessagePattern("customers", StringPattern, StringPattern)
        val scenario = Scenario("Test", HttpRequestPattern(), HttpResponsePattern(), emptyMap(), emptyList(), emptyMap(), emptyMap(), kafkaMessagePattern)

        val kafkaMessage = KafkaMessage("customers", StringValue("name"), StringValue("John Doe"))
        assertThat(scenario.matchesMock(kafkaMessage)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `will not match a mock http request with unexpected request headers`() {
        val scenario = Scenario("Test", HttpRequestPattern(method="GET", urlMatcher = URLMatcher(emptyMap(), emptyList(), "/"), headersPattern = HttpHeadersPattern(mapOf("X-Expected" to StringPattern))), HttpResponsePattern(status = 200), emptyMap(), emptyList(), emptyMap(), emptyMap(), null)
        val mockRequest = HttpRequest(method = "GET", path = "/", headers = mapOf("X-Expected" to "value", "X-Unexpected" to "value"))
        val mockResponse = HttpResponse.OK

        assertThat(scenario.matchesMock(mockRequest, mockResponse)).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `will not match a mock http request with unexpected response headers`() {
        val scenario = Scenario("Test", HttpRequestPattern(method="GET", urlMatcher = URLMatcher(emptyMap(), emptyList(), "/"), headersPattern = HttpHeadersPattern(emptyMap())), HttpResponsePattern(status = 200, headersPattern = HttpHeadersPattern(mapOf("X-Expected" to StringPattern))), emptyMap(), emptyList(), emptyMap(), emptyMap(), null)
        val mockRequest = HttpRequest(method = "GET", path = "/")
        val mockResponse = HttpResponse.OK.copy(headers = mapOf("X-Expected" to "value", "X-Unexpected" to "value"))

        assertThat(scenario.matchesMock(mockRequest, mockResponse)).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `will not match a mock http request with unexpected query params`() {
        val scenario = Scenario("Test", HttpRequestPattern(method="GET", urlMatcher = URLMatcher(mapOf("expected" to StringPattern), emptyList(), "/"), headersPattern = HttpHeadersPattern(emptyMap(), null)), HttpResponsePattern(status = 200), emptyMap(), emptyList(), emptyMap(), emptyMap(), null)
        val mockRequest = HttpRequest(method = "GET", path = "/", queryParams = mapOf("expected" to "value", "unexpected" to "value"))
        val mockResponse = HttpResponse.OK

        assertThat(scenario.matchesMock(mockRequest, mockResponse)).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `will not match a mock json body with unexpected keys`() {
        val scenario = Scenario("Test", HttpRequestPattern(method="POST", urlMatcher = URLMatcher(mapOf("expected" to StringPattern), emptyList(), "/"), headersPattern = HttpHeadersPattern(emptyMap(), null), body = parsedPattern("""{"expected": "value"}""")), HttpResponsePattern(status = 200), emptyMap(), emptyList(), emptyMap(), emptyMap(), null)
        val mockRequest = HttpRequest(method = "POST", path = "/", body = parsedValue("""{"unexpected": "value"}"""))
        val mockResponse = HttpResponse.OK

        assertThat(scenario.matchesMock(mockRequest, mockResponse)).isInstanceOf(Result.Failure::class.java)
    }
}
