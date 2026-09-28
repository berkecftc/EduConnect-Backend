package com.educonnect.gamificationservice;

import com.educonnect.common.test.PostgresTestContainer;
import com.educonnect.common.test.RabbitTestContainer;
import com.educonnect.common.test.TestJwtDecoder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
@Import({PostgresTestContainer.class, RabbitTestContainer.class, TestJwtDecoder.class})
public @interface GamificationIntegrationTest {
}
