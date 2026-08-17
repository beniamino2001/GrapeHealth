package it.pegasopw.grapehealth.decisionengine.repository;

import it.pegasopw.grapehealth.decisionengine.model.entity.SogliaIncubazioneGoidanichEntity;
import it.pegasopw.grapehealth.decisionengine.model.entity.SogliaIncubazioneGoidanichId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SogliaIncubazioneGoidanichRepository
        extends JpaRepository<SogliaIncubazioneGoidanichEntity, SogliaIncubazioneGoidanichId> {
}