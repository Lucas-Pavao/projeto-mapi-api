package com.projeto.mapi.mapper;

import com.projeto.mapi.dto.AlertDTO;
import com.projeto.mapi.dto.AlertRuleDTO;
import com.projeto.mapi.model.Alert;
import com.projeto.mapi.model.AlertRule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Arrays;
import java.util.List;

@Mapper
public interface AlertMapper {

    @Mapping(target = "ruleId", source = "rule.id")
    @Mapping(target = "floodPointSlug", source = "floodPoint.slug")
    AlertDTO toDto(Alert alert);

    @Mapping(target = "floodPointSlug", source = "floodPoint.slug")
    @Mapping(target = "channels", source = "channels")
    AlertRuleDTO toDto(AlertRule rule);

    default List<String> splitChannels(String channels) {
        return channels == null || channels.isBlank() ? List.of() : Arrays.asList(channels.split(","));
    }
}
