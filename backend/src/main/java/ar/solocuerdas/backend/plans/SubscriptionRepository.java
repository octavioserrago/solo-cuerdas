package ar.solocuerdas.backend.plans;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByProfileIdAndStatus(UUID profileId, String status);
}
