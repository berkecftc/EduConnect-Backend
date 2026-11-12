package com.educonnect.llmservice.util;

import com.educonnect.llmservice.dto.moderation.ModerationDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationDecisionParserTest {

    @Test
    void parsesZorbaLabel() {
        assertEquals(ModerationDecision.ZORBA, ModerationDecisionParser.parse("ZORBA").orElseThrow());
        assertEquals(ModerationDecision.ZORBA, ModerationDecisionParser.parse("zorba").orElseThrow());
    }

    @Test
    void parsesTemizLabel() {
        assertEquals(ModerationDecision.TEMIZ, ModerationDecisionParser.parse("TEMIZ").orElseThrow());
    }

    @Test
    void returnsEmptyForUnknown() {
        assertTrue(ModerationDecisionParser.parse("UNKNOWN").isEmpty());
    }
}

