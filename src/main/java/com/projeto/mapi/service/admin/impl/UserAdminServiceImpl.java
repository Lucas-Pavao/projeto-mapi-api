package com.projeto.mapi.service.admin.impl;

import com.projeto.mapi.dto.PageResponseDTO;
import com.projeto.mapi.dto.UserPatchRequestDTO;
import com.projeto.mapi.dto.UserSummaryDTO;
import com.projeto.mapi.exception.ConflictException;
import com.projeto.mapi.exception.InvalidRequestException;
import com.projeto.mapi.exception.ResourceNotFoundException;
import com.projeto.mapi.mapper.UserMapper;
import com.projeto.mapi.model.Role;
import com.projeto.mapi.model.User;
import com.projeto.mapi.repository.UserRepository;
import com.projeto.mapi.service.admin.UserAdminService;
import com.projeto.mapi.service.auth.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAdminServiceImpl implements UserAdminService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;
    private final com.projeto.mapi.security.UserAccessCache accessCache;
    private final com.projeto.mapi.service.stream.LiveStreamService streamService;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<UserSummaryDTO> list(int page, int size) {
        var result = userRepository.findAll(PageRequest.of(page, size, Sort.by("id")));
        return new PageResponseDTO<>(result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getContent().stream().map(userMapper::toSummary).toList());
    }

    @Override
    @Transactional
    public UserSummaryDTO update(Long id, UserPatchRequestDTO patch, String actingUsername) {
        if (patch.role() == null && patch.active() == null && patch.email() == null && patch.name() == null) {
            throw new InvalidRequestException("Informe ao menos um de role, active, email ou name");
        }
        User target = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: " + id));
        boolean touchesAccess = patch.role() != null || patch.active() != null;
        if (touchesAccess && target.getUsername().equals(actingUsername)) {
            throw new ConflictException("Um ADMIN não pode alterar o próprio perfil nem desativar a si mesmo");
        }
        if (patch.email() != null) {
            String email = patch.email().isBlank() ? null : patch.email().trim().toLowerCase(java.util.Locale.ROOT);
            if (email != null && userRepository.findByEmailIgnoreCase(email)
                    .filter(u -> !u.getId().equals(target.getId())).isPresent()) {
                throw new ConflictException("E-mail já cadastrado");
            }
            target.setEmail(email);
        }
        if (patch.name() != null) {
            target.setName(patch.name().isBlank() ? null : patch.name().trim());
        }

        Role newRole = patch.role() != null ? Role.valueOf(patch.role()) : target.getRole();
        boolean newActive = patch.active() != null ? patch.active() : target.isActive();

        boolean wasActiveAdmin = target.getRole() == Role.ADMIN && target.isActive();
        boolean willBeActiveAdmin = newRole == Role.ADMIN && newActive;
        if (wasActiveAdmin && !willBeActiveAdmin
                && userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new ConflictException("O sistema precisa manter ao menos um ADMIN ativo");
        }

        boolean accessChanged = newRole != target.getRole() || newActive != target.isActive();
        target.setRole(newRole);
        target.setActive(newActive);
        if (accessChanged) {
            // Revogação imediata (ADR 0010, emenda 2): os tokens emitidos com a versão antiga deixam de valer.
            target.setTokenVersion(target.getTokenVersion() + 1);
        }
        User saved = userRepository.save(target);
        if (accessChanged) {
            refreshTokenService.deleteByUserId(saved.getId());
            accessCache.evict(saved.getUsername());
            streamService.disconnect(saved.getUsername());
        } else {
            accessCache.evict(saved.getUsername()); // e-mail e nome também aparecem no principal
        }
        log.info("Usuário {} alterado por {}: role={}, active={}", saved.getUsername(), actingUsername, newRole, newActive);
        return userMapper.toSummary(saved);
    }
}
