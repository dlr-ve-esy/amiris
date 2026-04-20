// SPDX-FileCopyrightText: 2023-2026 German Aerospace Center <amiris@dlr.de>
//
// SPDX-License-Identifier: Apache-2.0
package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.stream.Collectors;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingException;
import de.dlr.gitlab.fame.agent.input.Make;
import de.dlr.gitlab.fame.agent.input.ParameterData;
import de.dlr.gitlab.fame.agent.input.ParameterData.MissingDataException;
import de.dlr.gitlab.fame.agent.input.Tree;

/** Caller for external model that is executed via post requests to a URL; <br>
 * usage; Create an anonymous (child) class with<br>
 * <code>UrlModelService&lt;RequestModel,ResponseModel&gt; myService = new UrlModelService&lt;RequestModel,ResponseModel&gt;(urlString) {}</code><br>
 * 
 * @param <T> POJO model of the <b>request</b> to be sent to external API
 * @param <U> POJO model of the <b>response</b> to be received from the external API
 * @author Christoph Schimeczek */
public abstract class UrlModelService<T extends JSONable, U> {
	static final String ERR_URL = "Service's URL is malformed: ";
	static final String ERR_RESULT_TYPE_MISSING = "Raw type is not allowed for result - please specify result type";
	static final String ERR_CONNECTION = "Could not open connection to service at: ";
	static final String ERR_RESPONSE = " : Request to service failed with code: ";
	static final String ERR_TIMEOUT = "Request timed out for service at: ";
	static final String ERR_GENERAL_IO = "Could not complete request to service at: ";
	static final String ERR_NO_JSON = " did not respond with a valid JSON String.";
	static final String ERR_MAPPING = " response not matching. Ensure response POJO model is a normal or >static inner< class, setter types match with service response, and all returned data from service are addressed.";

	/** Default timeout: indefinite */
	public static final int DEFAULT_TIMEOUT = 0;
	/** Inputs specific for {@link UrlModelService}s */
	public static final Tree parameters = Make.newTree().add(
			Make.newString("ServiceUrl").help("URL to which POST requests are sent; must begin with 'http://' or 'https://'"),
			Make.newInt("TimeoutInMilliseconds").optional().help("Max delay for service to respond (default: indefinite)"))
			.buildTree();

	private static Logger logger = LoggerFactory.getLogger(UrlModelService.class);
	private final Configuration configuration = Configuration.builder().mappingProvider(new JacksonMappingProvider())
			.jsonProvider(new JacksonJsonProvider()).build();

	private final URL serviceUrl;
	private final TypeRef<U> resultTypeRef;
	private int timeoutInMillis;

	/** Create a new {@link UrlModelService} as an anonymous (child) class, see also <a
	 * href=https://docs.oracle.com/javase/tutorial/java/javaOO/anonymousclasses.html>OracleDocs</a>
	 * 
	 * @param url of the model service API
	 * @param timeout max delay in milliseconds for the service to respond
	 * @throws IllegalArgumentException if URL is malformed */
	public UrlModelService(String url, int timeout) {
		serviceUrl = getUrl(url);
		resultTypeRef = getAnonymousResultTypeRef();
		this.timeoutInMillis = timeout;
	}

	/** @return given string converted to {@link URL} object */
	private URL getUrl(String url) {
		try {
			return new URL(url);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(ERR_URL + url, e);
		}
	}

	/** Creates an anonymous child class instance of {@link TypeRef} overriding its getType() function to return the result type of
	 * this UrlModelService.
	 * 
	 * @return special TypeRef instance bound to the second generic parameter of this UrlModelService */
	private TypeRef<U> getAnonymousResultTypeRef() {
		Type superClass = getClass().getGenericSuperclass();
		try {
			Type resultType = ((ParameterizedType) superClass).getActualTypeArguments()[1];
			return new TypeRef<U>() {
				@Override
				public Type getType() {
					return resultType;
				}
			};
		} catch (ClassCastException e) {
			throw new RuntimeException(ERR_RESULT_TYPE_MISSING, e);
		}
	}

	/** Create a new {@link UrlModelService} as an anonymous (child) class, see also <a
	 * href=https://docs.oracle.com/javase/tutorial/java/javaOO/anonymousclasses.html>OracleDocs</a>
	 * 
	 * @param url of the model service API */
	public UrlModelService(String url) {
		this(url, DEFAULT_TIMEOUT);
	}

	/** Create a new {@link UrlModelService} as an anonymous (child) class, see also <a
	 * href=https://docs.oracle.com/javase/tutorial/java/javaOO/anonymousclasses.html>OracleDocs</a>
	 * 
	 * @param input covering at least the remote service URL
	 * @throws MissingDataException if service URL is missing */
	public UrlModelService(ParameterData input) throws MissingDataException {
		this(input.getString("ServiceUrl"), input.getIntegerOrDefault("ReadTimeoutInMilliseconds", DEFAULT_TIMEOUT));
	}

	/** Marshalls given input to JSON, issues request to configured service and unmarshalls response to output format. See
	 * {@link JSONObject#JSONObject(Object) JSONObject} for help regarding the annotation of fields in input and response classes.
	 * 
	 * @param input POJO to be sent to service
	 * @return response from service as POJO */
	public U call(T input) {
		return unmarshall(call(marshall(input)));
	}

	/** @return given input translated to JSON String */
	private String marshall(T input) {
		return input.toJson().toString();
	}

	/** @return given response String translated to the result type */
	U unmarshall(String response) {
		try {
			ReadContext responseContext = JsonPath.using(configuration).parse(response);
			return responseContext.read("$", resultTypeRef);
		} catch (InvalidJsonException e) {
			throw new RuntimeException(serviceUrl + ERR_NO_JSON, e);
		} catch (MappingException e) {
			throw new RuntimeException(serviceUrl + ERR_MAPPING, e);
		}
	}

	/** Calls external model, submits given body and returns response
	 * 
	 * @param requestBody to be submitted to the external model
	 * @return response string from external model */
	private String call(String requestBody) {
		try {
			HttpURLConnection connection = connectToService();
			logger.info("Sending request to service at: " + serviceUrl);
			logger.debug(requestBody);
			sendBody(connection, requestBody);
			String response = readResponse(connection);
			connection.disconnect();
			logger.debug(response);
			logger.info("Response received from service at: " + serviceUrl);
			return response;
		} catch (IOException e) {
			throw new RuntimeException(ERR_GENERAL_IO + serviceUrl, e);
		}
	}

	/** @return new connection to the {@link #serviceUrl} */
	private HttpURLConnection connectToService() throws IOException {
		HttpURLConnection serviceConnection = (HttpURLConnection) serviceUrl.openConnection();
		serviceConnection.setRequestMethod("POST");
		serviceConnection.setRequestProperty("Content-Type", "application/json; charset=utf8");
		serviceConnection.setDoInput(true);
		serviceConnection.setDoOutput(true);
		serviceConnection.setReadTimeout(timeoutInMillis);
		return serviceConnection;
	}

	/** writes given request body to given connection */
	private void sendBody(HttpURLConnection connection, String requestBody) throws IOException {
		try {
			OutputStreamWriter wr = new OutputStreamWriter(connection.getOutputStream());
			wr.write(requestBody);
			wr.close();
		} catch (ConnectException e) {
			throw new RuntimeException(ERR_CONNECTION + serviceUrl, e);
		}
	}

	/** @return response from querying the service */
	private String readResponse(HttpURLConnection connection) throws IOException {
		String response;
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			response = reader.lines().collect(Collectors.joining(System.lineSeparator()));
			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
				throw new RuntimeException(connection.getResponseMessage() + ERR_RESPONSE + connection.getResponseCode());
			}
		} catch (SocketTimeoutException e) {
			throw new RuntimeException(ERR_TIMEOUT, e);
		}
		return response;
	}

	/** @return max delay for service to respond in milliseconds */
	public int getTimeout() {
		return timeoutInMillis;
	}

	/** Set max delay for service to respond
	 * 
	 * @param timeout in milliseconds */
	public void setTimeout(int timeout) {
		this.timeoutInMillis = timeout;
	}
}
