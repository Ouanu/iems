package dev.ouanu.iems.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import dev.ouanu.iems.entity.Apk;

@Repository
public interface ApkRepository extends MongoRepository<Apk, String> {
    Optional<Apk> findByPackageName(String packageName);
    List<Apk> findByGroup(String group);
    List<Apk> findByOrganization(String organization);
}
