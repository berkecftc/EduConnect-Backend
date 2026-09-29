package com.educonnect.authservices;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistrar;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@TestConfiguration(proxyBeanMethods = false)
public class AuthTestProperties {

	public static final String SERVICE_CLIENT_ID = "test-client";
	public static final String SERVICE_CLIENT_SECRET = "test-client-secret";

	@Bean
	public DynamicPropertyRegistrar authSigningKeyAndServiceClient() throws NoSuchAlgorithmException {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		String label = "PRIVATE" + " KEY";
		String pem = "-----BEGIN " + label + "-----\n"
				+ Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
				+ "\n-----END " + label + "-----";
		String secretHash = "{bcrypt}" + new BCryptPasswordEncoder(4).encode(SERVICE_CLIENT_SECRET);
		return registry -> {
			registry.add("jwt.private-key", () -> pem);
			registry.add("educonnect.security.service-clients." + SERVICE_CLIENT_ID, () -> secretHash);
		};
	}
}
