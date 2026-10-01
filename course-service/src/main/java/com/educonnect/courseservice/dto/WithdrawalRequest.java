package com.educonnect.courseservice.dto;

import jakarta.validation.constraints.Size;

public record WithdrawalRequest(@Size(max = 500) String reason) {
}
