package com.projeto.mapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {
    @NotBlank(message = "O nome de usuário é obrigatório")
    @Size(min = 3, max = 50, message = "O nome de usuário deve ter entre 3 e 50 caracteres")
    private String username;

    @NotBlank(message = "A senha é obrigatória")
    @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres")
    private String password;

    /** Token de convite (ADR 0010); define o perfil do novo usuário. Sem ele, o perfil é USER. */
    private String inviteToken;

    /** Opcional. Com convite que traga e-mail, vale o do convite. */
    @jakarta.validation.constraints.Email(message = "e-mail inválido")
    @Size(max = 255, message = "e-mail aceita até 255 caracteres")
    private String email;

    @Size(max = 255, message = "nome aceita até 255 caracteres")
    private String name;
}
