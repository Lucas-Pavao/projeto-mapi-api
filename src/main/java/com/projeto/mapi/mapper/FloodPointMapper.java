package com.projeto.mapi.mapper;

import com.projeto.mapi.dto.FloodPointPatchDTO;
import com.projeto.mapi.model.FloodPoint;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper
public interface FloodPointMapper {

    /** Aplica só os campos não nulos do PATCH. */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "municipality", ignore = true)
    @Mapping(target = "altitudeM", ignore = true)
    @Mapping(target = "distanceToChannelM", ignore = true)
    @Mapping(target = "basinName", ignore = true)
    void applyPatch(FloodPointPatchDTO patch, @MappingTarget FloodPoint point);
}
