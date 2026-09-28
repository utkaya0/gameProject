package com.project2.backend.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class EntityMappingTests {

    @Autowired
    private EntityManager entityManager;

    @Test
    void mapsLobbyAndBothGameModesToFlywaySchema() {
        Instant now = Instant.now();
        GuestSessionEntity guest = new GuestSessionEntity(UUID.randomUUID(), "Oyuncu", now);
        entityManager.persist(guest);

        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        LobbyEntity lobby = new LobbyEntity(code, guest, LobbyEntity.Status.WAITING,
                (short) 8, now, now.plusSeconds(3600));
        entityManager.persist(lobby);

        ParticipantEntity participant = new ParticipantEntity(lobby, guest, "Oyuncu",
                ParticipantEntity.Role.HOST, now, ParticipantEntity.ConnectionStatus.CONNECTED);
        entityManager.persist(participant);

        GameSessionEntity multiplayer = new GameSessionEntity("COLOR_GUESS",
                GameSessionEntity.Mode.MULTIPLAYER, lobby, GameSessionEntity.Status.IN_PROGRESS,
                (short) 1, (short) 5, "color-v1", now);
        entityManager.persist(multiplayer);

        RoundSubmissionEntity multiplayerGuess = new RoundSubmissionEntity(multiplayer, (short) 1,
                participant, "#3A7FD5", "#4285D0", new BigDecimal("9.420000"),
                new BigDecimal("3.250000"), 4231, now.plusSeconds(4));
        entityManager.persist(multiplayerGuess);

        GameSessionEntity solo = new GameSessionEntity("COLOR_GUESS",
                GameSessionEntity.Mode.SINGLE_PLAYER, null, GameSessionEntity.Status.IN_PROGRESS,
                (short) 1, (short) 5, "color-v1", now);
        entityManager.persist(solo);

        RoundSubmissionEntity soloGuess = new RoundSubmissionEntity(solo, (short) 1, null,
                "#3A7FD5", "#4285D0", new BigDecimal("9.420000"),
                new BigDecimal("3.250000"), 4231, now.plusSeconds(4));
        entityManager.persist(soloGuess);

        Long multiplayerGuessId = multiplayerGuess.getId();
        Long soloGuessId = soloGuess.getId();
        entityManager.flush();
        entityManager.clear();

        RoundSubmissionEntity loadedMultiplayer = entityManager.find(RoundSubmissionEntity.class, multiplayerGuessId);
        RoundSubmissionEntity loadedSolo = entityManager.find(RoundSubmissionEntity.class, soloGuessId);

        assertThat(loadedMultiplayer.getParticipant().getGuest().getGuestId()).isEqualTo(guest.getGuestId());
        assertThat(loadedMultiplayer.getGameSession().getLobby().getCode()).isEqualTo(code);
        assertThat(loadedMultiplayer.getScore()).isEqualByComparingTo("9.42");
        assertThat(loadedSolo.getParticipant()).isNull();
        assertThat(loadedSolo.getGameSession().getMode()).isEqualTo(GameSessionEntity.Mode.SINGLE_PLAYER);
        assertThat(loadedSolo.getGameSession().getConfigVersion()).isEqualTo("color-v1");
    }
}
