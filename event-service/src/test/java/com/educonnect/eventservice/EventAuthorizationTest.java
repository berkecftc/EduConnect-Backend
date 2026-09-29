package com.educonnect.eventservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.eventservice.Repository.EventParticipationRequestRepository;
import com.educonnect.eventservice.Repository.EventRegistrationRepository;
import com.educonnect.eventservice.Repository.EventRepository;
import com.educonnect.eventservice.client.ClubClient;
import com.educonnect.eventservice.dto.response.ClubAccess;
import com.educonnect.eventservice.model.Event;
import com.educonnect.eventservice.model.EventParticipationRequest;
import com.educonnect.eventservice.model.EventRegistration;
import com.educonnect.eventservice.model.EventStatus;
import com.educonnect.eventservice.model.ParticipationRequestStatus;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EventIntegrationTest
class EventAuthorizationTest {

	private static final Set<String> PRESIDENT = Set.of("VIEW_MEMBERS", "VIEW_MANAGEMENT_DATA",
			"MANAGE_MEMBERSHIP_REQUESTS", "PROPOSE_POSITION_CHANGE", "UPDATE_CLUB_PROFILE", "CREATE_EVENT",
			"MANAGE_EVENT_OPERATIONS");
	private static final Set<String> OFFICER = Set.of("VIEW_MEMBERS", "VIEW_MANAGEMENT_DATA", "MANAGE_EVENT_OPERATIONS");
	private static final Set<String> MEMBER = Set.of("VIEW_MEMBERS");
	private static final Set<String> ADVISOR = Set.of("VIEW_MEMBERS", "VIEW_MANAGEMENT_DATA", "ADVISE");

	private final UUID clubId = UUID.randomUUID();
	private final UUID otherClubId = UUID.randomUUID();
	private final UUID president = UUID.randomUUID();
	private final UUID officer = UUID.randomUUID();
	private final UUID member = UUID.randomUUID();
	private final UUID advisor = UUID.randomUUID();
	private final UUID otherOfficer = UUID.randomUUID();
	private final UUID otherAdvisor = UUID.randomUUID();
	private final UUID student = UUID.randomUUID();
	private final UUID admin = UUID.randomUUID();

	private final Map<List<UUID>, Set<String>> grants = new HashMap<>();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ClubClient clubClient;

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private EventParticipationRequestRepository participationRequestRepository;

	@Autowired
	private EventRegistrationRepository registrationRepository;

	private UUID eventId;
	private UUID pendingEventId;
	private UUID otherClubEventId;

	@BeforeEach
	void seed() {
		grant(clubId, president, PRESIDENT);
		grant(clubId, officer, OFFICER);
		grant(clubId, member, MEMBER);
		grant(clubId, advisor, ADVISOR);
		grant(otherClubId, otherOfficer, OFFICER);
		grant(otherClubId, otherAdvisor, ADVISOR);

		given(clubClient.getAccess(any(), any()))
				.willAnswer(call -> access(call.getArgument(0), call.getArgument(1)));
		given(clubClient.getUserAccess(any()))
				.willAnswer(call -> grants.keySet().stream()
						.filter(key -> key.get(1).equals(call.getArgument(0)))
						.map(key -> access(key.get(0), key.get(1)))
						.toList());
		given(clubClient.getClubIdsByAdvisorId(any()))
				.willAnswer(call -> grants.entrySet().stream()
						.filter(entry -> entry.getKey().get(1).equals(call.getArgument(0))
								&& entry.getValue().contains("ADVISE"))
						.map(entry -> entry.getKey().get(0))
						.toList());
		given(clubClient.isStudentMemberOfClub(any(), any()))
				.willAnswer(call -> {
					Set<String> permissions = grants.getOrDefault(
							List.of((UUID) call.getArgument(0), (UUID) call.getArgument(1)), Set.of());
					return permissions.contains("VIEW_MEMBERS") && !permissions.contains("ADVISE");
				});

		eventId = event(clubId, EventStatus.ACTIVE);
		pendingEventId = event(clubId, EventStatus.PENDING);
		otherClubEventId = event(otherClubId, EventStatus.ACTIVE);
	}

	@Test
	void pendingEventsAreHiddenFromEveryoneButTheClubsManagementAndAdvisor() throws Exception {
		mockMvc.perform(get("/api/events/{id}", eventId))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/events/{id}", pendingEventId))
				.andExpect(status().isNotFound());
		for (String token : new String[]{TestTokens.student(student), TestTokens.student(member),
				TestTokens.student(otherOfficer), TestTokens.academician(otherAdvisor), TestTokens.admin(admin)}) {
			mockMvc.perform(as(get("/api/events/{id}", pendingEventId), token))
					.andExpect(status().isNotFound());
		}
		mockMvc.perform(as(get("/api/events/{id}", pendingEventId), TestTokens.student(officer)))
				.andExpect(status().isOk());
		mockMvc.perform(as(get("/api/events/{id}", pendingEventId), TestTokens.academician(advisor)))
				.andExpect(status().isOk());
	}

	@Test
	void onlyThisClubsAdvisorApprovesAnEvent() throws Exception {
		String path = "/api/events/advisor/{eventId}/approve";
		mockMvc.perform(as(post(path, pendingEventId), TestTokens.student(president)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
		mockMvc.perform(as(post(path, pendingEventId), TestTokens.admin(admin)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
		mockMvc.perform(as(post(path, pendingEventId), TestTokens.academician(otherAdvisor)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
		assertThat(eventRepository.findById(pendingEventId).orElseThrow().getStatus()).isEqualTo(EventStatus.PENDING);

		mockMvc.perform(as(post(path, pendingEventId), TestTokens.academician(advisor)))
				.andExpect(status().isOk());
		assertThat(eventRepository.findById(pendingEventId).orElseThrow().getStatus()).isEqualTo(EventStatus.ACTIVE);
	}

	@Test
	void onlyThisClubsAdvisorRejectsAnEvent() throws Exception {
		String path = "/api/events/advisor/{eventId}/reject";
		mockMvc.perform(as(post(path, pendingEventId), TestTokens.academician(otherAdvisor)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(post(path, pendingEventId), TestTokens.student(officer)))
				.andExpect(status().isForbidden());
		assertThat(eventRepository.findById(pendingEventId).orElseThrow().getStatus()).isEqualTo(EventStatus.PENDING);

		mockMvc.perform(as(post(path, pendingEventId), TestTokens.academician(advisor)))
				.andExpect(status().isOk());
		assertThat(eventRepository.findById(pendingEventId).orElseThrow().getStatus()).isEqualTo(EventStatus.REJECTED);
	}

	@Test
	void advisorsListOnlyTheirOwnClubsPendingEvents() throws Exception {
		mockMvc.perform(as(get("/api/events/advisor/pending"), TestTokens.student(president)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/events/advisor/pending"), TestTokens.academician(otherAdvisor)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", not(hasItem(pendingEventId.toString()))));
		mockMvc.perform(as(get("/api/events/advisor/pending"), TestTokens.academician(advisor)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(pendingEventId.toString())));
	}

	@Test
	void onlyClubMembersAskToParticipate() throws Exception {
		String path = "/api/events/{eventId}/participation-request";
		mockMvc.perform(post(path, eventId))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
		mockMvc.perform(as(post(path, eventId), TestTokens.student(student)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(post(path, eventId), TestTokens.admin(admin)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(post(path, eventId), TestTokens.student(otherOfficer)))
				.andExpect(status().isForbidden());
		assertThat(participationRequestRepository.findByEventId(eventId)).isEmpty();

		mockMvc.perform(as(post(path, eventId), TestTokens.student(member)))
				.andExpect(status().isCreated());
		assertThat(participationRequestRepository.existsByEventIdAndStudentId(eventId, member)).isTrue();
	}

	@Test
	void participationIsApprovedOnlyByThisClubsOfficers() throws Exception {
		UUID requestId = participationRequest(member);
		String path = "/api/events/participation-requests/{requestId}/approve";
		for (String token : new String[]{TestTokens.student(member), TestTokens.academician(advisor),
				TestTokens.student(otherOfficer), TestTokens.admin(admin)}) {
			mockMvc.perform(as(post(path, requestId), token))
					.andExpect(status().isForbidden());
		}
		assertThat(participationRequestRepository.findById(requestId).orElseThrow().getStatus())
				.isEqualTo(ParticipationRequestStatus.PENDING);
		assertThat(registrationRepository.existsByEventIdAndStudentId(eventId, member)).isFalse();

		mockMvc.perform(as(post(path, requestId), TestTokens.student(officer)))
				.andExpect(status().isOk());
		assertThat(registrationRepository.existsByEventIdAndStudentId(eventId, member)).isTrue();
	}

	@Test
	void participationIsRejectedOnlyByThisClubsOfficers() throws Exception {
		UUID requestId = participationRequest(member);
		String path = "/api/events/participation-requests/{requestId}/reject";
		mockMvc.perform(as(post(path, requestId), TestTokens.student(otherOfficer)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(post(path, requestId), TestTokens.student(member)))
				.andExpect(status().isForbidden());
		assertThat(participationRequestRepository.findById(requestId).orElseThrow().getStatus())
				.isEqualTo(ParticipationRequestStatus.PENDING);

		mockMvc.perform(as(post(path, requestId), TestTokens.student(president)))
				.andExpect(status().isOk());
		assertThat(participationRequestRepository.findById(requestId).orElseThrow().getStatus())
				.isEqualTo(ParticipationRequestStatus.REJECTED);
	}

	@Test
	void participationRequestsOfAnEventAreVisibleOnlyToItsClubsOfficers() throws Exception {
		participationRequest(member);
		for (String path : new String[]{"/api/events/{eventId}/participation-requests/pending",
				"/api/events/{eventId}/participation-requests"}) {
			for (String token : new String[]{TestTokens.student(member), TestTokens.academician(advisor),
					TestTokens.student(otherOfficer), TestTokens.admin(admin)}) {
				mockMvc.perform(as(get(path, eventId), token))
						.andExpect(status().isForbidden());
			}
			mockMvc.perform(as(get(path, eventId), TestTokens.student(officer)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.length()").value(1));
		}
	}

	@Test
	void officialsSeePendingRequestsOnlyOfTheirOwnClubs() throws Exception {
		UUID requestId = participationRequest(member);
		mockMvc.perform(as(get("/api/events/official/pending-requests"), TestTokens.student(otherOfficer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", not(hasItem(requestId.toString()))));
		mockMvc.perform(as(get("/api/events/official/pending-requests"), TestTokens.student(officer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(requestId.toString())));
	}

	@Test
	void ticketsAreVerifiedOnlyByTheOrganisingClubsOfficers() throws Exception {
		String qr = ticket(eventId, member);
		String otherQr = ticket(otherClubEventId, student);
		for (String token : new String[]{TestTokens.student(member), TestTokens.academician(advisor),
				TestTokens.student(otherOfficer), TestTokens.admin(admin)}) {
			mockMvc.perform(json(post("/api/events/manage/verify-qr"), token, qrBody(qr)))
					.andExpect(status().isForbidden());
		}
		mockMvc.perform(json(post("/api/events/manage/verify-qr"), TestTokens.student(officer), qrBody(otherQr)))
				.andExpect(status().isForbidden());
		assertThat(registrationRepository.findByQrCode(qr).orElseThrow().isAttended()).isFalse();
		assertThat(registrationRepository.findByQrCode(otherQr).orElseThrow().isAttended()).isFalse();

		mockMvc.perform(json(post("/api/events/manage/verify-qr"), TestTokens.student(officer), qrBody(qr)))
				.andExpect(status().isOk());
		assertThat(registrationRepository.findByQrCode(qr).orElseThrow().isAttended()).isTrue();
	}

	@Test
	void registrantListIsVisibleToTheClubsOfficersAndAdvisorOnly() throws Exception {
		ticket(eventId, member);
		String path = "/api/events/manage/{eventId}/registrations";
		for (String token : new String[]{TestTokens.student(member), TestTokens.student(otherOfficer),
				TestTokens.academician(otherAdvisor), TestTokens.admin(admin)}) {
			mockMvc.perform(as(get(path, eventId), token))
					.andExpect(status().isForbidden());
		}
		mockMvc.perform(as(get(path, eventId), TestTokens.student(officer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
		mockMvc.perform(as(get(path, eventId), TestTokens.academician(advisor)))
				.andExpect(status().isOk());
	}

	@Test
	void clubEventManagementViewIsLimitedToTheClubsOfficersAndAdvisor() throws Exception {
		String path = "/api/events/manage/club/{clubId}/events";
		for (String token : new String[]{TestTokens.student(member), TestTokens.student(otherOfficer),
				TestTokens.academician(otherAdvisor), TestTokens.admin(admin)}) {
			mockMvc.perform(as(get(path, clubId), token))
					.andExpect(status().isForbidden());
		}
		mockMvc.perform(as(get(path, clubId), TestTokens.student(officer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(pendingEventId.toString())));
		mockMvc.perform(as(get(path, clubId), TestTokens.academician(advisor)))
				.andExpect(status().isOk());
	}

	@Test
	void managedEventListingsContainOnlyTheCallersClubs() throws Exception {
		mockMvc.perform(as(get("/api/events/manage/my-events"), TestTokens.student(otherOfficer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", not(hasItem(eventId.toString()))))
				.andExpect(jsonPath("$[*].id", hasItem(otherClubEventId.toString())));
		mockMvc.perform(as(get("/api/events/manage/my-events"), TestTokens.student(officer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", hasItem(pendingEventId.toString())))
				.andExpect(jsonPath("$[*].id", not(hasItem(otherClubEventId.toString()))));
		mockMvc.perform(as(get("/api/events/manage/my-events"), TestTokens.student(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void theAdminEventListIsAdminOnly() throws Exception {
		mockMvc.perform(as(get("/api/events/admin/all"), TestTokens.student(president)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/events/admin/all"), TestTokens.academician(advisor)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/events/admin/all"), TestTokens.admin(admin)))
				.andExpect(status().isOk());
	}

	@Test
	void eventCreationFailsBeforeTheRoleCheckWhenTheClubLookupIsUnreachable() throws Exception {
		mockMvc.perform(multipart("/api/events/manage").file(eventData()).file(poster()))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(as(multipart("/api/events/manage").file(eventData()).file(poster()), TestTokens.student(member)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));
		assertThat(eventRepository.findByClubId(clubId)).hasSize(2);
	}

	@Test
	void clubAuthorityLookupsFailClosed() throws Exception {
		UUID requestId = participationRequest(member);
		Request request = Request.create(Request.HttpMethod.GET, "http://club-service", Map.of(), null,
				StandardCharsets.UTF_8, null);
		willThrow(new FeignException.ServiceUnavailable("club-service down", request, null, null))
				.given(clubClient).getAccess(any(), any());

		mockMvc.perform(as(post("/api/events/participation-requests/{requestId}/approve", requestId),
						TestTokens.student(officer)))
				.andExpect(status().isServiceUnavailable());
		mockMvc.perform(as(get("/api/events/{id}", pendingEventId), TestTokens.student(officer)))
				.andExpect(status().isServiceUnavailable());
		assertThat(participationRequestRepository.findById(requestId).orElseThrow().getStatus())
				.isEqualTo(ParticipationRequestStatus.PENDING);
	}

	@Test
	void personalListingsRequireAToken() throws Exception {
		mockMvc.perform(get("/api/events/my-registrations"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/events/my-participation-requests"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/events/manage/my-events"))
				.andExpect(status().isUnauthorized());
	}

	private void grant(UUID club, UUID user, Set<String> permissions) {
		grants.put(List.of(club, user), permissions);
	}

	private ClubAccess access(UUID club, UUID user) {
		Set<String> permissions = grants.getOrDefault(List.of(club, user), Set.of());
		boolean advisorAccess = permissions.contains("ADVISE");
		boolean memberAccess = permissions.contains("VIEW_MEMBERS") && !advisorAccess;
		return new ClubAccess(club, user, null, memberAccess, permissions.contains("CREATE_EVENT"), advisorAccess,
				permissions);
	}

	private UUID event(UUID club, EventStatus status) {
		Event event = new Event();
		event.setTitle("Yetki Etkinliği");
		event.setEventTime(LocalDateTime.now().plusDays(7));
		event.setLocation("Konferans Salonu");
		event.setClubId(club);
		event.setClubName("Kulüp " + club);
		event.setStatus(status);
		return eventRepository.save(event).getId();
	}

	private UUID participationRequest(UUID studentId) {
		return participationRequestRepository.save(new EventParticipationRequest(eventId, studentId)).getId();
	}

	private String ticket(UUID event, UUID studentId) {
		EventRegistration registration = new EventRegistration();
		registration.setEventId(event);
		registration.setStudentId(studentId);
		registration.setQrCode(UUID.randomUUID().toString());
		return registrationRepository.save(registration).getQrCode();
	}

	private static String qrBody(String qr) {
		return "{\"qrCode\":\"" + qr + "\"}";
	}

	private MockMultipartFile eventData() {
		String json = "{\"title\":\"Yeni Etkinlik\",\"eventTime\":\"" + LocalDateTime.now().plusDays(10).withNano(0)
				+ "\",\"location\":\"Salon\",\"clubName\":\"Kulüp " + clubId + "\"}";
		return new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
	}

	private static MockMultipartFile poster() {
		return new MockMultipartFile("poster", "poster.png", MediaType.IMAGE_PNG_VALUE, Base64.getDecoder().decode(
				"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="));
	}

	private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
		return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
		return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
	}
}
