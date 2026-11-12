package com.educonnect.eventservice.dto.response;

public record ClubEventStatistics(long totalEvents,
                                  long completedEvents,
                                  long activeEvents,
                                  long cancelledEvents,
                                  long rejectedEvents,
                                  long pendingEvents,
                                  long registrations,
                                  long attendances) {
}
