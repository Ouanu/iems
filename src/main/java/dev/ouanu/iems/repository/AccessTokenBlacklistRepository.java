package dev.ouanu.iems.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import dev.ouanu.iems.entity.AccessTokenBlacklist;

@Repository
public interface AccessTokenBlacklistRepository extends MongoRepository<AccessTokenBlacklist, String>{
    boolean existsByJti(String jti);
}
