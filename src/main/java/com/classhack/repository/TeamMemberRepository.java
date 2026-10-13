package com.classhack.repository;

import com.classhack.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    List<TeamMember> findByTeamId(Long teamId);

    List<TeamMember> findByStudentId(Long studentId);

    Optional<TeamMember> findByTeamIdAndStudentId(Long teamId, Long studentId);

    @Query("SELECT COUNT(t) > 0 FROM Team t WHERE t.hackathon.id = :hackathonId AND t.deleted = false AND (t.teamLeader.id = :studentId OR EXISTS (SELECT m FROM TeamMember m WHERE m.team = t AND m.student.id = :studentId))")
    boolean isStudentInHackathonTeam(@Param("studentId") Long studentId, @Param("hackathonId") Long hackathonId);

    @Query("SELECT COUNT(DISTINCT m.student.id) FROM TeamMember m WHERE m.team.deleted = false")
    long countDistinctParticipants();

    @Query("SELECT COUNT(DISTINCT m.student.id) FROM TeamMember m WHERE m.team.hackathon.id = :hackathonId AND m.team.deleted = false")
    long countDistinctParticipantsByHackathonId(@Param("hackathonId") Long hackathonId);

    long countByTeamId(Long teamId);
}
