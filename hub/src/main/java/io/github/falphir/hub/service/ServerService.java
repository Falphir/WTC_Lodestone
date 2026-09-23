package io.github.falphir.hub.service;

import io.github.falphir.hub.entity.GameServer;
import io.github.falphir.hub.repository.GameServerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServerService {

    private final GameServerRepository repository;
    private final TokenService tokens;

    public ServerService(GameServerRepository repository, TokenService tokens) {
        this.repository = repository;
        this.tokens = tokens;
    }

    /** Registers a new server. The plain token is returned once and never stored. */
    @Transactional
    public RegisteredServer register(String id, String name) {
        if (repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Server '" + id + "' already exists");
        }
        String token = tokens.generateToken();
        repository.save(new GameServer(id, name, tokens.hash(token)));
        return new RegisteredServer(id, name, token);
    }

    public record RegisteredServer(String id, String name, String token) {}
}