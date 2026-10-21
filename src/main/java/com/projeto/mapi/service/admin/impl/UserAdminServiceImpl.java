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
        if (patch.role() == null && patch.active() == null) {
            throw new InvalidRequestException("Informe role e/ou active");
        }
        User target = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: " + id));
        if (target.getUsername().equals(actingUsername)) {
            throw new ConflictException("Um ADMIN não pode alterar o próprio perfil nem desativar a si mesmo");
        }

        Role newRole = patch.role() != null ? Role.valueOf(patch.role()) : target.getRole();
        boolean newActive = patch.active() != null ? patch.active() : target.isActive();

        boolean wasActiveAdmin = target.getRole() == Role.ADMIN && target.isActive();
        boolean willBeActiveAdmin = newRole == Role.ADMIN && newActive;
        if (wasActiveAdmin && !willBeActiveAdmin
                && userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new ConflictException("O sistema precisa manter ao menos um ADMIN ativo");
        }

        target.setRole(newRole);
        target.setActive(newActive);
        User saved = userRepository.save(target);
        if (!newActive) {
            refreshTokenService.deleteByUserId(saved.getId());
        }
        log.info("Usuário {} alterado por {}: role={}, active={}", saved.getUsername(), actingUsername, newRole, newActive);
        return userMapper.toSummary(saved);
    }
}
