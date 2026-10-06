package com.classhack.repository;

import com.classhack.entity.Team;
import com.classhack.entity.enums.TeamStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByHackathonIdAndDeletedFalseOrderByCreatedAtDesc(Long hackathonId);

    Optional<Team> findByIdAndDeletedFalse(Long id);

    boolean existsByTeamNameIgnoreCaseAndHackathonIdAndDeletedFalse(String teamName, Long hackathonId);

    long countByDeletedFalse();

    long countByStatusAndDeletedFalse(TeamStatus status);

    long countByHackathonIdAndDeletedFalse(Long hackathonId);

    long countByHackathonIdAndStatusAndDeletedFalse(Long hackathonId, TeamStatus status);

    @Query("SELECT t FROM Team t JOIN t.members m WHERE m.student.id = :studentId AND t.deleted = false ORDER BY t.createdAt DESC")
    List<Team> findTeamsByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT t FROM Team t JOIN t.members m WHERE m.student.id = :studentId AND t.hackathon.id = :hackathonId AND t.deleted = false")
    Optional<Team> findTeamByStudentAndHackathon(@Param("studentId") Long studentId, @Param("hackathonId") Long hackathonId);
}
