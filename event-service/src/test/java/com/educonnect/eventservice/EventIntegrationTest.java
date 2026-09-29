package com.educonnect.eventservice;

import com.educonnect.common.test.MinioTestContainer;
import com.educonnect.common.test.PostgresTestContainer;
import com.educonnect.common.test.RabbitTestContainer;
import com.educonnect.common.test.RedisTestContainer;
import com.educonnect.common.test.TestJwtDecoder;
import com.educonnect.eventservice.client.ClubClient;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresTestContainer.class, RabbitTestContainer.class, RedisTestContainer.class, MinioTestContainer.class,
		TestJwtDecoder.class})
@MockitoBean(types = ClubClient.class)
public @interface EventIntegrationTest {
}
