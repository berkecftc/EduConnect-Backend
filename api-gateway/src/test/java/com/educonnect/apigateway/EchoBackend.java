package com.educonnect.apigateway;

import com.educonnect.apigateway.filter.AuthenticationFilter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@TestConfiguration(proxyBeanMethods = false)
public class EchoBackend {

	public static final String ECHO_USER_ID = "Echo-User-Id";
	public static final String ECHO_USER_EMAIL = "Echo-User-Email";
	public static final String ECHO_USER_ROLES = "Echo-User-Roles";

	@Bean
	public Server echoServer() throws IOException {
		return new Server();
	}

	@Bean
	public RouteLocator echoRoutes(RouteLocatorBuilder builder, AuthenticationFilter authenticationFilter, Server echoServer) {
		return builder.routes()
				.route("echo-routes", r -> r
						.path("/api/auth/**", "/api/clubs/**", "/api/events/**", "/api/posts/**", "/api/users/**")
						.filters(f -> f.filter(authenticationFilter.apply(new AuthenticationFilter.Config())))
						.uri("http://localhost:" + echoServer.port()))
				.build();
	}

	public static final class Server implements DisposableBean {

		private final HttpServer httpServer;
		private final Queue<String> received = new ConcurrentLinkedQueue<>();

		Server() throws IOException {
			httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			httpServer.createContext("/", exchange -> {
				received.add(exchange.getRequestURI().getRawPath());
				copy(exchange.getRequestHeaders().getFirst("X-Authenticated-User-Id"), ECHO_USER_ID, exchange.getResponseHeaders());
				copy(exchange.getRequestHeaders().getFirst("X-Authenticated-User-Email"), ECHO_USER_EMAIL, exchange.getResponseHeaders());
				copy(exchange.getRequestHeaders().getFirst("X-Authenticated-User-Roles"), ECHO_USER_ROLES, exchange.getResponseHeaders());
				byte[] body = "echo".getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(200, body.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(body);
				}
			});
			httpServer.start();
		}

		public int port() {
			return httpServer.getAddress().getPort();
		}

		public List<String> receivedPaths() {
			return List.copyOf(received);
		}

		public void clear() {
			received.clear();
		}

		@Override
		public void destroy() {
			httpServer.stop(0);
		}

		private static void copy(String value, String name, Headers target) {
			if (value != null) {
				target.add(name, value);
			}
		}
	}
}
