package com.educonnect.authservices;

import com.educonnect.common.test.MinioTestContainer;
import com.educonnect.common.test.PostgresTestContainer;
import com.educonnect.common.test.RabbitTestContainer;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestContainer.class, RabbitTestContainer.class, MinioTestContainer.class,
		AuthTestProperties.class})
public @interface AuthIntegrationTest {
}
