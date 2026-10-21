package com.projeto.mapi.mapper;

import com.projeto.mapi.dto.UserSummaryDTO;
import com.projeto.mapi.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface UserMapper {

    UserSummaryDTO toSummary(User user);
}
