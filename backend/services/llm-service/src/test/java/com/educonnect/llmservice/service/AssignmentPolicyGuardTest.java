package com.educonnect.llmservice.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssignmentPolicyGuardTest {

    private static final String STUDENT = "s1";

    private final PendingAssignments pending = mock(PendingAssignments.class);
    private final AssignmentPolicyGuard guard = new AssignmentPolicyGuard(pending);

    @Test
    void forbiddenAssignmentsAreAnsweredWithoutTheModelUnlessOnlyTheDeadlineIsAsked() {
        when(pending.all(STUDENT)).thenReturn(List.of(assignment("Bağlı Liste Uygulaması", "NONE")));

        AssignmentPolicyGuard.Decision help = guard.evaluate(STUDENT, "Bağlı liste ödevinde ekleme fonksiyonu için ipucu verir misin?");
        assertThat(help.reply()).contains("Bağlı Liste Uygulaması", "izin vermiyor", "12 Ekim 2026 12:00");

        AssignmentPolicyGuard.Decision deadline = guard.evaluate(STUDENT, "BAĞLI LİSTE UYGULAMASI ne zaman teslim?");
        assertThat(deadline.reply()).isNull();
        assertThat(deadline.systemNote()).contains("NOT allowed");
    }

    @Test
    void otherPoliciesAddTheAssignmentRuleToTheSystemPrompt() {
        when(pending.all(STUDENT)).thenReturn(List.of(
                assignment("Döngüler", "GUIDANCE"), assignment("Sıralama Algoritmaları Raporu", "ALLOWED_WITH_DISCLOSURE")));

        assertThat(guard.evaluate(STUDENT, "döngüler ödevinin kodunu yazar mısın").systemNote())
                .contains("\"Döngüler\"", "BIL201", "Never write the answer");
        assertThat(guard.evaluate(STUDENT, "sıralama raporu için yardım").systemNote()).contains("declare");
        assertThat(guard.evaluate(STUDENT, "kulüpler hakkında bilgi").systemNote()).isNull();
    }

    @Test
    void failuresAndAmbiguityFallBackToTheGeneralRules() {
        when(pending.all(STUDENT)).thenThrow(new IllegalStateException("down"));
        assertThat(guard.evaluate(STUDENT, "Bağlı Liste Uygulaması")).isEqualTo(new AssignmentPolicyGuard.Decision(null, null));
        assertThat(AssignmentPolicyGuard.mentions("liste ödevi", "bağlı liste uygulaması")).isFalse();
    }

    private static PendingAssignments.PendingAssignment assignment(String title, String policy) {
        return new PendingAssignments.PendingAssignment("BIL201", "Veri Yapıları", title, "Ödev", "12 Ekim 2026 12:00",
                "Teslim edilmedi", policy, PendingAssignments.aiHelp(policy));
    }
}
