package com.projeto.mapi.mapper;

import com.projeto.mapi.dto.UserSummaryDTO;
import com.projeto.mapi.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface UserMapper {

    @Mapping(target = "name", expression = "java(null)")
    @Mapping(target = "email", source = "username")
    UserSummaryDTO toSummary(User user);
}
