package com.onafriq.paylite.service.paylite_service.repository;


import com.onafriq.paylite.service.paylite_service.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
    Optional<IdempotencyKey> findByKey(String key);
    boolean existsByKey(String key);
}