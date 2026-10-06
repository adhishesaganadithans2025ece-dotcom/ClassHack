package com.classhack.repository;

import com.classhack.entity.Hackathon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HackathonRepository extends JpaRepository<Hackathon, Long> {

    List<Hackathon> findByDeletedFalseOrderByRegistrationStartDesc();

    Optional<Hackathon> findByIdAndDeletedFalse(Long id);

    Optional<Hackathon> findFirstByNameIgnoreCaseAndDeletedFalse(String name);

    @Query("SELECT h FROM Hackathon h WHERE h.deleted = false AND (" +
            ":keyword IS NULL OR :keyword = '' OR " +
            "LOWER(h.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(h.organizedBy) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(h.venue) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY h.registrationStart DESC")
    List<Hackathon> searchHackathons(@Param("keyword") String keyword);

    long countByDeletedFalse();
}
