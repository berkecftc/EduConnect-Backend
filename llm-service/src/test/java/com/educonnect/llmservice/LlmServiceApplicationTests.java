package com.educonnect.llmservice;

import com.educonnect.common.test.RabbitTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(RabbitTestContainer.class)
class LlmServiceApplicationTests {

    @Autowired
    @Qualifier("clubVectorStore")
    private VectorStore clubVectorStore;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void startsWithRealBrokerAndHealthIsUp() throws Exception {
        assertThat(clubVectorStore).isNotNull();

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
