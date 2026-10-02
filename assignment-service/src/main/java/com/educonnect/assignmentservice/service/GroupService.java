package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.client.UserClient;
import com.educonnect.assignmentservice.dto.GroupRequest;
import com.educonnect.assignmentservice.dto.GroupSetRequest;
import com.educonnect.assignmentservice.dto.GroupSetResponse;
import com.educonnect.assignmentservice.model.CourseGroup;
import com.educonnect.assignmentservice.model.GroupMember;
import com.educonnect.assignmentservice.model.GroupSet;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import com.educonnect.assignmentservice.repository.CourseGroupRepository;
import com.educonnect.assignmentservice.repository.GroupMemberRepository;
import com.educonnect.assignmentservice.repository.GroupSetRepository;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class GroupService {

    private final GroupSetRepository setRepository;
    private final CourseGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final StudentDirectory studentDirectory;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final StudentAssignmentCache studentCache;
    private final Clock clock = Clock.systemDefaultZone();

    public GroupService(GroupSetRepository setRepository,
                        CourseGroupRepository groupRepository,
                        GroupMemberRepository memberRepository,
                        StudentDirectory studentDirectory,
                        AssignmentRepository assignmentRepository,
                        SubmissionRepository submissionRepository,
                        StudentAssignmentCache studentCache) {
        this.setRepository = setRepository;
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.studentDirectory = studentDirectory;
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.studentCache = studentCache;
    }

    @Transactional(readOnly = true)
    public GroupSet set(UUID setId) {
        return setRepository.findById(setId)
                .orElseThrow(() -> new NotFoundException("GROUP_SET_NOT_FOUND", "Grup seti bulunamadı."));
    }

    @Transactional(readOnly = true)
    public CourseGroup group(UUID groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("GROUP_NOT_FOUND", "Grup bulunamadı."));
    }

    @Transactional(readOnly = true)
    public List<GroupSetResponse> list(UUID courseId, UUID viewerId, boolean staff) {
        List<GroupSet> sets = setRepository.findByCourseIdOrderByNameAsc(courseId);
        List<UUID> setIds = sets.stream().map(GroupSet::getId).toList();
        if (setIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<CourseGroup>> groups = groupRepository.findByGroupSetIdInOrderByNameAsc(setIds).stream()
                .collect(Collectors.groupingBy(CourseGroup::getGroupSetId));
        List<GroupMember> members = memberRepository.findByGroupSetIdIn(setIds);
        Map<UUID, List<GroupMember>> byGroup = members.stream().collect(Collectors.groupingBy(GroupMember::getGroupId));
        Map<UUID, UUID> myGroups = members.stream().filter(m -> m.getStudentId().equals(viewerId))
                .collect(Collectors.toMap(GroupMember::getGroupSetId, GroupMember::getGroupId, (a, b) -> a));
        Map<UUID, UserClient.UserProfileDTO> students = studentDirectory.byId(
                members.stream().filter(m -> staff || m.getGroupId().equals(myGroups.get(m.getGroupSetId())))
                        .map(GroupMember::getStudentId).toList());
        LocalDateTime now = LocalDateTime.now(clock);
        return sets.stream().map(set -> {
            UUID myGroup = myGroups.get(set.getId());
            List<GroupSetResponse.Group> groupViews = groups.getOrDefault(set.getId(), List.of()).stream().map(group -> {
                List<GroupMember> groupMembers = byGroup.getOrDefault(group.getId(), List.of());
                boolean showMembers = staff || group.getId().equals(myGroup);
                return new GroupSetResponse.Group(group.getId(), group.getName(), groupMembers.size(),
                        showMembers ? groupMembers.stream().map(m -> member(m, students.get(m.getStudentId()))).toList() : List.of());
            }).toList();
            return new GroupSetResponse(set.getId(), set.getCourseId(), set.getName(), set.isSelfSignup(), set.getMaxMembers(),
                    set.getSignupClosesAt(), set.signupOpenAt(now), myGroup, groupViews);
        }).toList();
    }

    public GroupSet createSet(UUID courseId, UUID actorId, GroupSetRequest request) {
        String name = request.name().strip();
        if (setRepository.existsByCourseIdAndNameIgnoreCase(courseId, name)) {
            throw new ConflictException("GROUP_SET_EXISTS", "Bu derste aynı adlı bir grup seti var.");
        }
        GroupSet set = new GroupSet(courseId, actorId);
        apply(set, request, name);
        return setRepository.save(set);
    }

    public GroupSet updateSet(GroupSet set, GroupSetRequest request) {
        String name = request.name().strip();
        if (!name.equalsIgnoreCase(set.getName()) && setRepository.existsByCourseIdAndNameIgnoreCase(set.getCourseId(), name)) {
            throw new ConflictException("GROUP_SET_EXISTS", "Bu derste aynı adlı bir grup seti var.");
        }
        apply(set, request, name);
        return setRepository.save(set);
    }

    public void deleteSet(GroupSet set) {
        if (assignmentRepository.existsByGroupSetId(set.getId())) {
            throw new ConflictException("GROUP_SET_IN_USE", "Bu grup seti bir ödevde kullanılıyor; önce ödevin grup ayarını kaldırın.");
        }
        studentCache.evict(memberRepository.findByGroupSetIdIn(List.of(set.getId())).stream().map(GroupMember::getStudentId).toList());
        setRepository.delete(set);
    }

    public CourseGroup createGroup(GroupSet set, GroupRequest request) {
        String name = request.name().strip();
        if (groupRepository.existsByGroupSetIdAndNameIgnoreCase(set.getId(), name)) {
            throw new ConflictException("GROUP_EXISTS", "Bu sette aynı adlı bir grup var.");
        }
        return groupRepository.save(new CourseGroup(set.getId(), name));
    }

    public void deleteGroup(CourseGroup group) {
        if (submissionRepository.existsByGroupId(group.getId())) {
            throw new ConflictException("GROUP_HAS_SUBMISSIONS", "Teslim yapmış grup silinemez.");
        }
        studentCache.evict(memberRepository.findByGroupId(group.getId()).stream().map(GroupMember::getStudentId).toList());
        groupRepository.delete(group);
    }

    public void addMember(CourseGroup group, UUID studentId, UUID actorId) {
        CourseGroup locked = lock(group.getId());
        GroupSet set = set(locked.getGroupSetId());
        Optional<GroupMember> current = memberRepository.findByGroupSetIdAndStudentId(set.getId(), studentId);
        if (current.isPresent() && current.get().getGroupId().equals(locked.getId())) {
            return;
        }
        requireRoom(set, locked);
        Instant now = Instant.now(clock);
        if (current.isPresent()) {
            current.get().moveTo(locked, actorId, now);
            memberRepository.save(current.get());
        } else {
            memberRepository.save(new GroupMember(locked, studentId, actorId, now));
        }
        studentCache.evict(List.of(studentId));
    }

    public void removeMember(CourseGroup group, UUID studentId) {
        GroupMember member = memberRepository.findByGroupSetIdAndStudentId(group.getGroupSetId(), studentId)
                .filter(m -> m.getGroupId().equals(group.getId()))
                .orElseThrow(() -> new NotFoundException("GROUP_MEMBER_NOT_FOUND", "Öğrenci bu grupta değil."));
        memberRepository.delete(member);
        studentCache.evict(List.of(studentId));
    }

    public void join(CourseGroup group, UUID studentId) {
        CourseGroup locked = lock(group.getId());
        GroupSet set = set(locked.getGroupSetId());
        requireSignupOpen(set);
        if (memberRepository.findByGroupSetIdAndStudentId(set.getId(), studentId).isPresent()) {
            throw new ConflictException("ALREADY_IN_GROUP", "Bu sette zaten bir gruptasınız; önce ayrılın.");
        }
        requireRoom(set, locked);
        memberRepository.save(new GroupMember(locked, studentId, studentId, Instant.now(clock)));
        studentCache.evict(List.of(studentId));
    }

    public void leave(CourseGroup group, UUID studentId) {
        requireSignupOpen(set(group.getGroupSetId()));
        if (submissionRepository.existsByGroupId(group.getId())) {
            throw new ConflictException("GROUP_HAS_SUBMISSIONS", "Grubunuz teslim yaptığı için gruptan ayrılamazsınız; hocanıza başvurun.");
        }
        removeMember(group, studentId);
    }

    private CourseGroup lock(UUID groupId) {
        return groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new NotFoundException("GROUP_NOT_FOUND", "Grup bulunamadı."));
    }

    private void requireRoom(GroupSet set, CourseGroup group) {
        if (set.getMaxMembers() != null && memberRepository.countByGroupId(group.getId()) >= set.getMaxMembers()) {
            throw new ConflictException("GROUP_FULL", "Grup kontenjanı dolu.");
        }
    }

    private void requireSignupOpen(GroupSet set) {
        if (!set.signupOpenAt(LocalDateTime.now(clock))) {
            throw new ConflictException("SIGNUP_CLOSED", "Bu grup setinde öğrenci seçimi kapalı.");
        }
    }

    private static void apply(GroupSet set, GroupSetRequest request, String name) {
        set.setName(name);
        set.setSelfSignup(Boolean.TRUE.equals(request.selfSignup()));
        set.setMaxMembers(request.maxMembers());
        set.setSignupClosesAt(request.signupClosesAt());
    }

    private static GroupSetResponse.Member member(GroupMember member, UserClient.UserProfileDTO profile) {
        return new GroupSetResponse.Member(member.getStudentId(),
                profile != null ? profile.getFirstName() + " " + profile.getLastName() : "Bilinmeyen Öğrenci",
                profile != null ? profile.getStudentNumber() : null, member.getJoinedAt());
    }
}
