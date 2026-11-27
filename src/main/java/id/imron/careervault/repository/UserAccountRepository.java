package id.imron.careervault.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import id.imron.careervault.domain.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);
}
