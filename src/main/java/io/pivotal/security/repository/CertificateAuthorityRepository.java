package io.pivotal.security.repository;

import io.pivotal.security.entity.NamedCertificateAuthority;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CertificateAuthorityRepository extends JpaRepository<NamedCertificateAuthority, UUID> {
  List<NamedCertificateAuthority> findAllByNameIgnoreCaseOrderByVersionCreatedAtDesc(String name);
  NamedCertificateAuthority findFirstByNameIgnoreCaseOrderByVersionCreatedAtDesc(String name);
  NamedCertificateAuthority findOneByUuid(UUID uuid);
  List<NamedCertificateAuthority> findByEncryptionKeyUuidNot(UUID uuid);
}