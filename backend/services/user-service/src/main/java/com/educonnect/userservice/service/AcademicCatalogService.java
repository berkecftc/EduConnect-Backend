package com.educonnect.userservice.service;

import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import com.educonnect.userservice.dto.request.AcademicUnitRequest;
import com.educonnect.userservice.dto.response.AcademicCatalogResponse;
import com.educonnect.userservice.dto.response.AcademicPlacement;
import com.educonnect.userservice.models.Department;
import com.educonnect.userservice.models.Faculty;
import com.educonnect.userservice.models.Program;
import com.educonnect.userservice.repository.DepartmentRepository;
import com.educonnect.userservice.repository.FacultyRepository;
import com.educonnect.userservice.repository.ProgramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AcademicCatalogService {

    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final ProgramRepository programRepository;
    private final Clock clock = Clock.systemDefaultZone();

    public AcademicCatalogService(FacultyRepository facultyRepository,
                                  DepartmentRepository departmentRepository,
                                  ProgramRepository programRepository) {
        this.facultyRepository = facultyRepository;
        this.departmentRepository = departmentRepository;
        this.programRepository = programRepository;
    }

    @Transactional(readOnly = true)
    public List<AcademicCatalogResponse> catalog(boolean includeInactive) {
        Map<UUID, List<Program>> programs = programRepository.findAll().stream()
                .filter(p -> includeInactive || p.isActive())
                .collect(Collectors.groupingBy(Program::getDepartmentId));
        Map<UUID, List<Department>> departments = departmentRepository.findAll().stream()
                .filter(d -> includeInactive || d.isActive())
                .collect(Collectors.groupingBy(Department::getFacultyId));
        return facultyRepository.findAll().stream()
                .filter(f -> includeInactive || f.isActive())
                .sorted(Comparator.comparing(Faculty::getName))
                .map(f -> new AcademicCatalogResponse(f.getId(), f.getCode(), f.getName(), f.isActive(),
                        departments.getOrDefault(f.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(Department::getName))
                                .map(d -> new AcademicCatalogResponse.Department(d.getId(), d.getCode(), d.getName(), d.isActive(),
                                        programs.getOrDefault(d.getId(), List.of()).stream()
                                                .sorted(Comparator.comparing(Program::getName))
                                                .map(p -> new AcademicCatalogResponse.Program(p.getId(), p.getCode(), p.getName(),
                                                        p.getLevel(), p.getDurationYears(), p.isActive()))
                                                .toList()))
                                .toList()))
                .toList();
    }

    public Faculty saveFaculty(UUID id, AcademicUnitRequest request) {
        Faculty faculty = id == null ? new Faculty() : facultyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("FACULTY_NOT_FOUND", "Fakülte bulunamadı."));
        String code = code(request);
        if (!code.equalsIgnoreCase(Optional.ofNullable(faculty.getCode()).orElse("")) && facultyRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("CODE_EXISTS", "Bu kodla bir fakülte var.");
        }
        faculty.setCode(code);
        faculty.setName(request.name().strip());
        faculty.setActive(!Boolean.FALSE.equals(request.active()));
        return facultyRepository.save(faculty);
    }

    public Department saveDepartment(UUID id, AcademicUnitRequest request) {
        Department department = id == null ? new Department() : departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("DEPARTMENT_NOT_FOUND", "Bölüm bulunamadı."));
        UUID facultyId = request.parentId() != null ? request.parentId() : department.getFacultyId();
        if (facultyId == null || !facultyRepository.existsById(facultyId)) {
            throw new BadRequestException("FACULTY_NOT_FOUND", "Bölümün fakültesi bulunamadı.");
        }
        String code = code(request);
        if (!code.equalsIgnoreCase(Optional.ofNullable(department.getCode()).orElse("")) && departmentRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("CODE_EXISTS", "Bu kodla bir bölüm var.");
        }
        department.setFacultyId(facultyId);
        department.setCode(code);
        department.setName(request.name().strip());
        department.setActive(!Boolean.FALSE.equals(request.active()));
        return departmentRepository.save(department);
    }

    public Program saveProgram(UUID id, AcademicUnitRequest request) {
        Program program = id == null ? new Program() : programRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("PROGRAM_NOT_FOUND", "Program bulunamadı."));
        UUID departmentId = request.parentId() != null ? request.parentId() : program.getDepartmentId();
        if (departmentId == null || !departmentRepository.existsById(departmentId)) {
            throw new BadRequestException("DEPARTMENT_NOT_FOUND", "Programın bölümü bulunamadı.");
        }
        if (request.level() == null || request.durationYears() == null) {
            throw new BadRequestException("PROGRAM_LEVEL_REQUIRED", "Programın düzeyi ve süresi zorunludur.");
        }
        String code = code(request);
        if (!code.equalsIgnoreCase(Optional.ofNullable(program.getCode()).orElse("")) && programRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("CODE_EXISTS", "Bu kodla bir program var.");
        }
        program.setDepartmentId(departmentId);
        program.setCode(code);
        program.setName(request.name().strip());
        program.setLevel(request.level());
        program.setDurationYears(request.durationYears());
        program.setActive(!Boolean.FALSE.equals(request.active()));
        return programRepository.save(program);
    }

    @Transactional(readOnly = true)
    public AcademicPlacement program(UUID programId) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new NotFoundException("PROGRAM_NOT_FOUND", "Program bulunamadı."));
        Department department = departmentRepository.findById(program.getDepartmentId()).orElseThrow();
        Faculty faculty = facultyRepository.findById(department.getFacultyId()).orElseThrow();
        return new AcademicPlacement(faculty.getId(), faculty.getName(), department.getId(), department.getName(),
                program.getId(), program.getName(), program.getLevel(), program.getDurationYears(),
                program.isActive() && department.isActive() && faculty.isActive());
    }

    @Transactional(readOnly = true)
    public AcademicPlacement faculty(UUID facultyId) {
        Faculty faculty = facultyRepository.findById(facultyId)
                .orElseThrow(() -> new NotFoundException("FACULTY_NOT_FOUND", "Fakülte bulunamadı."));
        return new AcademicPlacement(faculty.getId(), faculty.getName(), null, null, null, null, null, null, faculty.isActive());
    }

    @Transactional(readOnly = true)
    public AcademicPlacement department(UUID departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new NotFoundException("DEPARTMENT_NOT_FOUND", "Bölüm bulunamadı."));
        Faculty faculty = facultyRepository.findById(department.getFacultyId()).orElseThrow();
        return new AcademicPlacement(faculty.getId(), faculty.getName(), department.getId(), department.getName(),
                null, null, null, null, department.isActive() && faculty.isActive());
    }

    public Integer classYear(Integer entryYear) {
        if (entryYear == null) {
            return null;
        }
        LocalDate today = LocalDate.now(clock);
        int academicStartYear = today.getMonthValue() >= Month.SEPTEMBER.getValue() ? today.getYear() : today.getYear() - 1;
        return Math.max(1, academicStartYear - entryYear + 1);
    }

    private static String code(AcademicUnitRequest request) {
        return request.code().strip().toUpperCase(Locale.ROOT);
    }
}
