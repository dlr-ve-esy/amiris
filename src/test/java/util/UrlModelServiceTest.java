// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static testUtils.Exceptions.assertThrowsMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;

public class UrlModelServiceTest {

	public class Input implements JSONable {}

	public class Result {}

	public static class ResultA {
		double x = Double.NaN;

		public void setX(double x) {
			this.x = x;
		}
	}

	private final String validURL = "http://localhost:8000/endpoint";

	private HttpURLConnection mockedConnection;

	@BeforeEach
	public void setup() {
		mockedConnection = mock(HttpURLConnection.class);
	}

	@Test
	public void constructor_malformedUrl_throws() {
		assertThrowsMessage(IllegalArgumentException.class, UrlModelService.ERR_URL,
				() -> new UrlModelService<Input, Result>("<not!A>URL") {});
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void constructor_resultTypeMissing_throws() {
		assertThrowsMessage(RuntimeException.class, UrlModelService.ERR_RESULT_TYPE_MISSING,
				() -> new UrlModelService(validURL) {});
	}

	@Test
	public void constructor_noTimeoutProvided_useDefault() {
		UrlModelService<Input, Result> ums = new UrlModelService<Input, Result>(validURL) {};
		assert ums.getTimeout() == UrlModelService.DEFAULT_TIMEOUT;
	}

	@Test
	public void setTimeout_applied() {
		UrlModelService<Input, Result> ums = new UrlModelService<Input, Result>(validURL) {};
		ums.setTimeout(123);
		assert ums.getTimeout() == 123;
	}

	@Test
	public void constructor_providedTimeoutUsed() {
		UrlModelService<Input, Result> ums = new UrlModelService<Input, Result>(validURL, 125) {};
		assert ums.getTimeout() == 125;
	}

	@Test
	public void constructor_missingURL_throws() throws MissingDataException {
		ParameterData mockedInput = mock(ParameterData.class);
		MissingDataException ex = mock(MissingDataException.class);
		when(mockedInput.getString("ServiceUrl")).thenThrow(ex);
		when(ex.getMessage()).thenReturn("");

		assertThrowsMessage(MissingDataException.class, "", () -> new UrlModelService<Input, Result>(mockedInput) {});
	}

	@Test
	public void call_serviceUnreachable_throws() {
		UrlModelService<Input, Result> ums = new UrlModelService<Input, Result>(validURL, 1) {};
		assertThrowsMessage(RuntimeException.class, UrlModelService.ERR_CONNECTION, () -> ums.call(new Input()));
	}

	@Test
	public void call_serviceTimeout_throws() throws IOException {
		UrlModelService<Input, Result> ums = injectUrlMock(Input.class, Result.class, 0);
		try (MockedConstruction<OutputStreamWriter> mockOSW = Mockito.mockConstruction(OutputStreamWriter.class)) {
			when(mockedConnection.getInputStream()).thenThrow(new SocketTimeoutException());
			assertThrowsMessage(RuntimeException.class, UrlModelService.ERR_TIMEOUT, () -> ums.call(new Input()));
		}
	}

	private <U extends JSONable, T> UrlModelService<U, T> injectUrlMock(Class<U> u, Class<T> t, int timeout) {
		try (MockedConstruction<URL> mocked = Mockito.mockConstruction(URL.class,
				(mock, context) -> {
					when(mock.openConnection()).thenReturn(mockedConnection);
				})) {
			return new UrlModelService<U, T>(validURL, timeout) {};
		}
	}

	@Test
	public void call_responseNotOK_throws() throws IOException {
		UrlModelService<Input, Result> ums = injectUrlMock(Input.class, Result.class, 0);
		try (MockedConstruction<OutputStreamWriter> mockOSW = Mockito.mockConstruction(OutputStreamWriter.class);
				MockedConstruction<InputStreamReader> mockISR = Mockito.mockConstruction(InputStreamReader.class);
				MockedConstruction<BufferedReader> mockBR = Mockito.mockConstruction(BufferedReader.class);) {
			when(mockedConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_BAD_REQUEST);
			assertThrowsMessage(RuntimeException.class, UrlModelService.ERR_RESPONSE, () -> ums.call(new Input()));
		}
	}

	@Test
	public void unmarshall_noJSON_throws() {
		UrlModelService<Input, Result> ums = new UrlModelService<Input, Result>(validURL, 0) {};
		assertThrowsMessage(RuntimeException.class, UrlModelService.ERR_NO_JSON, () -> ums.unmarshall("[;Not JSON}"));
	}

	@Test
	public void unmarshall_typeMismatch_throws() {
		UrlModelService<Input, ResultA> ums = new UrlModelService<Input, ResultA>(validURL, 0) {};
		assertThrowsMessage(RuntimeException.class, UrlModelService.ERR_MAPPING,
				() -> ums.unmarshall("{\"x\": \"not a double\"}"));
	}

	@Test
	public void unmarshall_missingData_returnsBlank() {
		UrlModelService<Input, ResultA> ums = new UrlModelService<Input, ResultA>(validURL, 0) {};
		ResultA result = ums.unmarshall("{}");
		assert Double.isNaN(result.x);
	}

	@Test
	public void unmarshall_responseContent_considered() {
		UrlModelService<Input, ResultA> ums = new UrlModelService<Input, ResultA>(validURL, 0) {};
		ResultA result = ums.unmarshall("{\"x\": 5.0}");
		assert result.x == 5.;
	}
}
