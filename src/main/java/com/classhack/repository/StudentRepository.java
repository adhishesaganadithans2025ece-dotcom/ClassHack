package com.classhack.repository;

import com.classhack.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByEmailIgnoreCase(String email);

    Optional<Student> findByStudentIdIgnoreCase(String studentId);

    Optional<Student> findByRollNumberIgnoreCase(String rollNumber);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByStudentIdIgnoreCase(String studentId);

    boolean existsByRollNumberIgnoreCase(String rollNumber);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Student s WHERE s.id = :id")
    Optional<Student> findByIdWithLock(@Param("id") Long id);

    List<Student> findByActiveTrueOrderByFullNameAsc();

    List<Student> findAllByOrderByCreatedAtDesc();

    @Query("SELECT s FROM Student s WHERE " +
            "(:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(s.studentId) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.rollNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.department) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.section) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.year) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY s.fullName ASC")
    List<Student> searchStudents(@Param("keyword") String keyword);

    @Query("SELECT s FROM Student s WHERE s.active = true AND (" +
            "LOWER(s.studentId) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(s.rollNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(s.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(s.department) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "ORDER BY s.fullName ASC")
    List<Student> searchActiveStudentsForTeam(@Param("query") String query);

    List<Student> findByDepartmentIgnoreCaseAndSectionIgnoreCaseOrderByCreatedAtDesc(String department, String section);

    @Query("SELECT s FROM Student s WHERE " +
            "LOWER(s.department) = LOWER(:department) AND LOWER(s.section) = LOWER(:section) AND (" +
            ":keyword IS NULL OR :keyword = '' OR " +
            "LOWER(s.studentId) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.rollNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.year) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY s.fullName ASC")
    List<Student> searchStudentsByDeptAndSection(@Param("keyword") String keyword, @Param("department") String department, @Param("section") String section);

    long countByDepartmentIgnoreCaseAndSectionIgnoreCaseAndActiveTrue(String department, String section);

    long countByActiveTrue();
}
