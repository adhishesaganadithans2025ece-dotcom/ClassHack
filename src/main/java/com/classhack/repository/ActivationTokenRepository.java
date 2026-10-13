package com.classhack.repository;

import com.classhack.entity.ActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActivationTokenRepository extends JpaRepository<ActivationToken, Long> {

    Optional<ActivationToken> findByTokenHash(String tokenHash);

    List<ActivationToken> findByEmailIgnoreCaseAndUsedFalse(String email);

    Optional<ActivationToken> findTopByEmailIgnoreCaseOrderByCreatedAtDesc(String email);
}
