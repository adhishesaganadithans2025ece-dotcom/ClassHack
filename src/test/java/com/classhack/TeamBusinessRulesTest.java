package com.classhack;

import com.classhack.dto.TeamCreateRequest;
import com.classhack.dto.TeamResponse;
import com.classhack.entity.Hackathon;
import com.classhack.entity.Student;
import com.classhack.entity.enums.Gender;
import com.classhack.entity.enums.HackathonMode;
import com.classhack.entity.enums.Role;
import com.classhack.entity.enums.TeamStatus;
import com.classhack.exception.BadRequestException;
import com.classhack.exception.DuplicateResourceException;
import com.classhack.repository.HackathonRepository;
import com.classhack.repository.StudentRepository;
import com.classhack.repository.TeamRepository;
import com.classhack.service.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TeamBusinessRulesTest {

    @Autowired
    private TeamService teamService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private HackathonRepository hackathonRepository;

    @Autowired
    private TeamRepository teamRepository;

    private Student student1;
    private Student student2;
    private Student student3;
    private Hackathon openHackathon;
    private Hackathon closedHackathon;

    @BeforeEach
    void setUp() {
        student1 = studentRepository.save(Student.builder()
                .studentId("ECE001")
                .fullName("Adhishesagan")
                .gender(Gender.MALE)
                .department("ECE")
                .section("A")
                .year("III")
                .academicYear("2026-27")
                .email("adhi@college.edu")
                .password("$2a$10$dummyHash...")
                .role(Role.ROLE_STUDENT)
                .active(true)
                .build());

        student2 = studentRepository.save(Student.builder()
                .studentId("ECE002")
                .fullName("Arun")
                .gender(Gender.MALE)
                .department("ECE")
                .section("A")
                .year("III")
                .academicYear("2026-27")
                .email("arun@college.edu")
                .password("$2a$10$dummyHash...")
                .role(Role.ROLE_STUDENT)
                .active(true)
                .build());

        student3 = studentRepository.save(Student.builder()
                .studentId("CSE001")
                .fullName("Priya")
                .gender(Gender.FEMALE)
                .department("CSE")
                .section("B")
                .year("III")
                .academicYear("2026-27")
                .email("priya@college.edu")
                .password("$2a$10$dummyHash...")
                .role(Role.ROLE_STUDENT)
                .active(true)
                .build());

        openHackathon = hackathonRepository.save(Hackathon.builder()
                .name("Smart India Hackathon 2026")
                .description("National level hackathon")
                .organizedBy("Govt of India")
                .registrationStart(LocalDate.now().minusDays(5))
                .registrationEnd(LocalDate.now().plusDays(5))
                .maximumTeamSize(3)
                .prizeMoney(100000.0)
                .mode(HackathonMode.ONLINE)
                .registrationLink("https://sih.gov.in")
                .build());

        closedHackathon = hackathonRepository.save(Hackathon.builder()
                .name("Old Hackathon")
                .description("Past event")
                .organizedBy("College")
                .registrationStart(LocalDate.now().minusDays(20))
                .registrationEnd(LocalDate.now().minusDays(5))
                .maximumTeamSize(4)
                .prizeMoney(10000.0)
                .mode(HackathonMode.OFFLINE)
                .venue("Auditorium")
                .registrationLink("https://college.edu/hack")
                .build());
    }

    @Test
    @DisplayName("Rule: Student can successfully create a team with registered students")
    void testCreateTeamSuccess() {
        TeamCreateRequest req = TeamCreateRequest.builder()
                .teamName("Electro Nova")
                .memberStudentIds(List.of(student2.getId()))
                .build();

        TeamResponse res = teamService.createTeam(openHackathon.getId(), req, student1.getId());

        assertNotNull(res);
        assertEquals("Electro Nova", res.getTeamName());
        assertEquals(TeamStatus.REGISTERED, res.getStatus());
        assertEquals(2, res.getMemberCount());
        assertEquals("Adhishesagan", res.getTeamLeaderName());
    }

    @Test
    @DisplayName("Rule 9: Cannot create team after registration deadline has passed")
    void testCannotCreateTeamAfterDeadline() {
        TeamCreateRequest req = TeamCreateRequest.builder()
                .teamName("Late Team")
                .memberStudentIds(List.of(student2.getId()))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                teamService.createTeam(closedHackathon.getId(), req, student1.getId())
        );

        assertTrue(ex.getMessage().contains("registration is closed") || ex.getMessage().contains("deadline"));
    }

    @Test
    @DisplayName("Rule 2: Student cannot belong to two teams in the same hackathon")
    void testStudentCannotBelongToTwoTeamsInSameHackathon() {
        TeamCreateRequest req1 = TeamCreateRequest.builder()
                .teamName("Team Alpha")
                .memberStudentIds(List.of(student2.getId()))
                .build();

        teamService.createTeam(openHackathon.getId(), req1, student1.getId());

        // student1 tries to create another team
        TeamCreateRequest req2 = TeamCreateRequest.builder()
                .teamName("Team Beta")
                .memberStudentIds(List.of(student3.getId()))
                .build();

        BadRequestException ex1 = assertThrows(BadRequestException.class, () ->
                teamService.createTeam(openHackathon.getId(), req2, student1.getId())
        );
        assertTrue(ex1.getMessage().contains("already registered in a team"));

        // student3 tries to create a team adding student2 who is already in Team Alpha
        TeamCreateRequest req3 = TeamCreateRequest.builder()
                .teamName("Team Gamma")
                .memberStudentIds(List.of(student2.getId()))
                .build();

        BadRequestException ex2 = assertThrows(BadRequestException.class, () ->
                teamService.createTeam(openHackathon.getId(), req3, student3.getId())
        );
        assertTrue(ex2.getMessage().contains("already belongs to another team"));
    }

    @Test
    @DisplayName("Rule 3: Team size cannot exceed maximum team size")
    void testTeamSizeLimit() {
        // openHackathon max team size is 3 (1 leader + 2 members max)
        Student student4 = studentRepository.save(Student.builder()
                .studentId("MECH001")
                .fullName("Karthik")
                .gender(Gender.MALE)
                .department("MECH")
                .section("A")
                .year("II")
                .academicYear("2026-27")
                .email("karthik@college.edu")
                .password("$2a$10$dummyHash...")
                .role(Role.ROLE_STUDENT)
                .active(true)
                .build());

        // Trying to add 3 members + 1 leader = 4 > max 3
        TeamCreateRequest req = TeamCreateRequest.builder()
                .teamName("Too Big Team")
                .memberStudentIds(List.of(student2.getId(), student3.getId(), student4.getId()))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                teamService.createTeam(openHackathon.getId(), req, student1.getId())
        );

        assertTrue(ex.getMessage().contains("exceeds the maximum allowed team size"));
    }

    @Test
    @DisplayName("Rule 4: Team name must be unique within the same hackathon")
    void testTeamNameUniqueWithinHackathon() {
        TeamCreateRequest req1 = TeamCreateRequest.builder()
                .teamName("Code Titans")
                .memberStudentIds(List.of())
                .build();

        teamService.createTeam(openHackathon.getId(), req1, student1.getId());

        TeamCreateRequest req2 = TeamCreateRequest.builder()
                .teamName("Code Titans")
                .memberStudentIds(List.of())
                .build();

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class, () ->
                teamService.createTeam(openHackathon.getId(), req2, student2.getId())
        );

        assertTrue(ex.getMessage().contains("already registered for this hackathon"));
    }
}
