package com.shadhinpay.identity.mapper;

import com.shadhinpay.identity.dto.MerchantOnboardingDto;
import com.shadhinpay.identity.entity.MerchantProfile;
import com.shadhinpay.identity.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MerchantProfileMapper {

  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "status", source = "user.status")
  @Mapping(target = "identifier", source = "user.identifier")
  @Mapping(target = "identifierType", source = "user.identifierType")
  @Mapping(target = "fullName", source = "profile.fullName")
  @Mapping(target = "onboardingStatus", source = "profile.onboardingStatus")
  MerchantOnboardingDto toDto(User user, MerchantProfile profile);
}
